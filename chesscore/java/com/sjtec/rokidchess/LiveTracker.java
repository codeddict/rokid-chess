package com.sjtec.rokidchess;

import com.github.bhlangonijr.chesslib.Board;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.*;
import org.opencv.features2d.*;
import org.opencv.imgproc.Imgproc;
import java.util.*;

/** On-device planar tracking + changed-square matching. No model, network or image history. */
final class LiveTracker implements AutoCloseable {
    static final int SIZE=384;
    private final ORB orb=ORB.create(1400);
    private final DescriptorMatcher matcher=DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
    private Mat reference=new Mat(),refDescriptors=new Mat(),refToBoard=new Mat(),baseline=new Mat();
    private MatOfKeyPoint refKeys=new MatOfKeyPoint();
    private final MoveObservation consensus=new MoveObservation();
    boolean calibrated(){return !baseline.empty();}
    static final class Frame implements AutoCloseable {
        final Mat raw,warped,toBoard; final String move,promotion,reason;final boolean stable;
        Frame(Mat raw,Mat warped,Mat h,String move,String promotion,String reason,boolean stable) {
            this.raw=raw;this.warped=warped;toBoard=h;this.move=move;this.promotion=promotion;this.reason=reason;this.stable=stable;
        }
        @Override public void close(){raw.release();warped.release();toBoard.release();}
        float[] cameraCorners(){
            if(toBoard.empty()||warped.empty())return null;
            Mat inverse=toBoard.inv();MatOfPoint2f source=canonical(),destination=new MatOfPoint2f();
            try{Core.perspectiveTransform(source,destination,inverse);Point[] p=destination.toArray();float[] result=new float[8];for(int i=0;i<4;i++){result[i*2]=(float)p[i].x;result[i*2+1]=(float)p[i].y;}return result;}
            finally{inverse.release();source.release();destination.release();}
        }
    }
    void calibrate(Mat image,Point[] corners) {
        if(image.empty()||corners.length!=4)throw new IllegalArgumentException("체스판 네 모서리를 선택하세요.");
        double area=0;int sign=0;
        for(int i=0;i<4;i++) {
            Point a=corners[i],b=corners[(i+1)%4],c=corners[(i+2)%4];
            if(a.x<0||a.y<0||a.x>=image.cols()||a.y>=image.rows()||Math.hypot(a.x-b.x,a.y-b.y)<70)
                throw new IllegalArgumentException("모서리 범위/간격을 확인하세요.");
            double cross=(b.x-a.x)*(c.y-b.y)-(b.y-a.y)*(c.x-b.x);
            if(Math.abs(cross)<1||sign!=0&&Math.signum(cross)!=sign)throw new IllegalArgumentException("a8 → h8 → h1 → a1 순서로 누르세요.");
            sign=cross>0?1:-1;area+=a.x*b.y-a.y*b.x;
        }
        if(Math.abs(area)*.5<image.total()*.06)throw new IllegalArgumentException("체스판이 너무 작습니다. 가까이 바라보세요.");
        MatOfPoint2f from=new MatOfPoint2f(corners),to=canonical();
        Mat h=Imgproc.getPerspectiveTransform(from,to),warped=new Mat();
        try { Imgproc.warpPerspective(image,warped,h,new Size(SIZE,SIZE));replaceReference(image,warped,h); }
        finally{from.release();to.release();h.release();warped.release();}
    }
    private static MatOfPoint2f canonical(){return new MatOfPoint2f(new Point(0,0),new Point(SIZE-1,0),new Point(SIZE-1,SIZE-1),new Point(0,SIZE-1));}
    private void replaceReference(Mat raw,Mat warped,Mat h) {
        reference.release();baseline.release();refToBoard.release();refDescriptors.release();refKeys.release();
        reference=raw.clone();baseline=warped.clone();refToBoard=h.clone();
        refKeys=new MatOfKeyPoint();refDescriptors=new Mat();
        Mat gray=gray(raw),mask=new Mat();
        try{orb.detectAndCompute(gray,mask,refKeys,refDescriptors);}finally{gray.release();mask.release();}
        consensus.reset();
        if(refDescriptors.rows()<30) {reset();throw new IllegalArgumentException("판의 특징이 부족합니다. 조명/초점을 확인하세요.");}
    }
    void reset() {baseline.release();baseline=new Mat();consensus.reset();}
    Frame observe(Mat input,Board board,long now) {
        Mat raw=input.clone(),warped=new Mat(),h=new Mat();
        if(!calibrated())return new Frame(raw,warped,h,"","","판 맞추기가 필요합니다.",false);
        Mat gray=gray(raw),descriptors=new Mat(),mask=new Mat();MatOfKeyPoint keys=new MatOfKeyPoint();
        List<MatOfDMatch> pairs=new ArrayList<>();
        MatOfPoint2f src=new MatOfPoint2f(),dst=new MatOfPoint2f();Mat inliers=new Mat(),alignment=new Mat();
        try {
            orb.detectAndCompute(gray,mask,keys,descriptors);
            if(descriptors.rows()<30) return rejected(raw,warped,h,"초점 / 가림 · 판을 바라보세요.");
            matcher.knnMatch(descriptors,refDescriptors,pairs,2);
            KeyPoint[] current=keys.toArray(),old=refKeys.toArray();List<Point> source=new ArrayList<>(),target=new ArrayList<>();
            Set<Integer> used=new HashSet<>();
            for(MatOfDMatch pair:pairs) {
                DMatch[] m=pair.toArray();
                if(m.length==2 && m[0].distance<.70*m[1].distance && used.add(m[0].trainIdx)) {
                    source.add(current[m[0].queryIdx].pt);target.add(old[m[0].trainIdx].pt);
                }
            }
            if(source.size()<30)return rejected(raw,warped,h,"판 추적 중 · 잠시 머리를 멈추세요.");
            src.fromList(source);dst.fromList(target);
            alignment.release();alignment=Calib3d.findHomography(src,dst,Calib3d.RANSAC,2.5,inliers,1500,.995);
            int good=Core.countNonZero(inliers);
            if(alignment.empty()||good<24||good<source.size()*.5)return rejected(raw,warped,h,"판 추적 불안정 · 자동 반영 보류");
            // Geometric support must cover the board, not just one moved piece or hand.
            MatOfPoint2f targetPoints=new MatOfPoint2f(),boardPoints=new MatOfPoint2f();
            Set<Integer> tiles=new HashSet<>();
            try{
                targetPoints.fromList(target);Core.perspectiveTransform(targetPoints,boardPoints,refToBoard);
                Point[] pts=boardPoints.toArray();
                for(int i=0;i<pts.length;i++)if(inliers.get(i,0)[0]!=0) {
                    Point p=pts[i];if(p.x>=0&&p.y>=0&&p.x<SIZE&&p.y<SIZE)tiles.add((int)(p.y/(SIZE/3)) *3+(int)(p.x/(SIZE/3)));
                }
            }finally{targetPoints.release();boardPoints.release();}
            if(tiles.size()<4)return rejected(raw,warped,h,"판 전체가 보이게 바라보세요.");
            Mat unused=new Mat();try{Core.gemm(refToBoard,alignment,1,unused,0,h);}finally{unused.release();}
            if(!visible(h,raw.cols(),raw.rows()))return rejected(raw,warped,h,"체스판이 화면 밖에 있습니다.");
            Imgproc.warpPerspective(raw,warped,h,new Size(SIZE,SIZE));
            double[] changes=differences(baseline,warped);
            MoveObservation.Match match=MoveObservation.match(board,changes);
            if(match.uci.isEmpty()) {consensus.reset();return new Frame(raw,warped,h,"",match.promotion,match.reason,false);}
            boolean stable=consensus.stable(match.uci,now);
            return new Frame(raw,warped,h,match.uci,"",stable?"자동 반영":"변화 안정성 확인 중",stable);
        }catch(RuntimeException e){return rejected(raw,warped,h,"판 추적 실패 · 다시 바라보세요.");}
        finally{gray.release();descriptors.release();mask.release();keys.release();src.release();dst.release();inliers.release();alignment.release();for(MatOfDMatch p:pairs)p.release();}
    }
    private Frame rejected(Mat raw,Mat warped,Mat h,String reason){consensus.reset();return new Frame(raw,warped,h,"","",reason,false);}
    private static boolean visible(Mat h,int width,int height) {
        Mat inverse=h.inv();MatOfPoint2f corners=canonical(),mapped=new MatOfPoint2f();
        try {
            Core.perspectiveTransform(corners,mapped,inverse);Point[] p=mapped.toArray();double area=0;
            for(int i=0;i<4;i++) {
                Point a=p[i],b=p[(i+1)%4];
                if(!Double.isFinite(a.x)||a.x<3||a.y<3||a.x>=width-3||a.y>=height-3)return false;
                area+=a.x*b.y-a.y*b.x;
            }
            return Math.abs(area)*.5>width*height*.05;
        }finally{inverse.release();corners.release();mapped.release();}
    }
    static double[] differences(Mat before,Mat after) {
        Mat a=gray(before),b=gray(after),diff=new Mat();double[] result=new double[64];
        try {
            Imgproc.GaussianBlur(a,a,new Size(3,3),.7);Imgproc.GaussianBlur(b,b,new Size(3,3),.7);
            // Global brightness offset is estimated with the median square mean.
            double[] shifts=new double[64];
            for(int i=0;i<64;i++) {
                Rect r=cell(i);Mat aa=a.submat(r),bb=b.submat(r);
                shifts[i]=Core.mean(bb).val[0]-Core.mean(aa).val[0];aa.release();bb.release();
            }
            Arrays.sort(shifts);Core.subtract(b,new Scalar((shifts[31]+shifts[32])*.5),b);
            Core.absdiff(a,b,diff);
            for(int i=0;i<64;i++) {Mat roi=diff.submat(cell(i));result[i]=Core.mean(roi).val[0]/255;roi.release();}
            return result;
        }finally{a.release();b.release();diff.release();}
    }
    private static Rect cell(int i){return new Rect(i%8*48+6,i/8*48+6,36,36);}
    private static Mat gray(Mat input){Mat out=new Mat();if(input.channels()==1)input.copyTo(out);else Imgproc.cvtColor(input,out,Imgproc.COLOR_BGR2GRAY);return out;}
    void accept(Frame frame){replaceReference(frame.raw,frame.warped,frame.toBoard);}
    // clear() changes an algorithm's state; it does not release the owning Java Ptr.
    // These reusable wrappers are finalized with this tracker; release all per-frame Mats explicitly.
    @Override public void close(){reference.release();refDescriptors.release();refToBoard.release();baseline.release();refKeys.release();}
}
