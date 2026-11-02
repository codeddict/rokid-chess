package com.sjtec.rokidchess;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Separate bundled Stockfish process with bounded waits and no shell/network access. */
final class UciEngine implements AutoCloseable {
    static final class Line {
        final String move; final int cp; final Integer mate;
        Line(String m,int c,Integer mate) { move=m;cp=c;this.mate=mate; }
    }
    private final String path;
    private volatile Process process;
    private BufferedWriter input;
    private final BlockingQueue<String> output=new LinkedBlockingQueue<>(4096);
    private volatile boolean closed;
    UciEngine(String executable) { path=executable; }
    private void start() throws Exception {
        if(closed) throw new IOException("엔진이 종료되었습니다.");
        if(process!=null && process.isAlive()) return;
        output.clear();
        process=new ProcessBuilder(path).redirectErrorStream(true).start();
        Process current=process;
        if(closed){current.destroy();process=null;throw new IOException("엔진이 종료되었습니다.");}
        input=new BufferedWriter(new OutputStreamWriter(current.getOutputStream(),StandardCharsets.UTF_8));
        Thread reader=new Thread(() -> {
            try(BufferedReader lines=new BufferedReader(new InputStreamReader(current.getInputStream(),StandardCharsets.UTF_8))) {
                String s; while((s=lines.readLine())!=null) { if(process==current&&!output.offer(s)) { output.poll(); output.offer(s); } }
            } catch(IOException ignored) { }
            if(process==current)output.offer("__exit__");
        },"chess-uci-output"); reader.setDaemon(true); reader.start();
        send("uci"); until("uciok",10000);
        send("setoption name Threads value 2"); send("setoption name Hash value 32");
        send("isready"); until("readyok",10000);
    }
    private void send(String text) throws IOException { input.write(text);input.newLine();input.flush(); }
    private String next(long deadline) throws Exception {
        long remaining=deadline-System.nanoTime();
        String s=remaining<=0?null:output.poll(remaining,TimeUnit.NANOSECONDS);
        if(s==null||s.equals("__exit__")) throw new IOException("휴대폰 체스 엔진 응답 시간 초과");
        return s;
    }
    private void until(String prefix,long ms) throws Exception {
        long end=System.nanoTime()+ms*1000000;
        while(!next(end).startsWith(prefix)) { }
    }
    List<Line> analyse(String root,List<String> moves,int millis,int count,String rootMove) throws Exception {
        try {
            start(); send("isready"); until("readyok",3000);
            send("setoption name MultiPV value "+Math.max(1,count));
            send("position fen "+root+(moves.isEmpty()?"":" moves "+String.join(" ",moves)));
            send("go movetime "+millis+(rootMove==null?"":" searchmoves "+rootMove));
            Map<Integer,Line> lines=new TreeMap<>();
            long end=System.nanoTime()+(millis+5000L)*1000000;
            String best="";
            while(true) {
                String s=next(end);
                if(s.startsWith("bestmove ")) {best=s.split(" ")[1];break;}
                parse(s,lines);
            }
            List<Line> result=new ArrayList<>(lines.values());
            if(result.isEmpty() && best.matches("[a-h][1-8][a-h][1-8][qrbn]?")) result.add(new Line(best,0,null));
            if(result.isEmpty()) throw new IOException("추천 가능한 수가 없습니다.");
            result.sort((a,b)->Integer.compare(b.cp,a.cp)); return result;
        } catch(Exception e) { kill(); throw e; }
    }
    static void parse(String s,Map<Integer,Line> lines) {
        if(!s.startsWith("info ")||!s.contains(" pv ")||s.contains("upperbound")||s.contains("lowerbound"))return;
        String[] t=s.split(" +");int index=1;Integer cp=null,mate=null;String move=null;
        try {
            for(int i=0;i<t.length;i++) {
                if(t[i].equals("multipv")) index=Integer.parseInt(t[++i]);
                else if(t[i].equals("score")) {
                    String kind=t[++i];int value=Integer.parseInt(t[++i]);
                    if(kind.equals("mate")) {mate=value;cp=value>0?100000-value:-100000-value;}
                    else if(kind.equals("cp"))cp=value;
                } else if(t[i].equals("pv")) {move=t[i+1];break;}
            }
            if(cp!=null && move!=null && move.matches("[a-h][1-8][a-h][1-8][qrbn]?")) lines.put(index,new Line(move,cp,mate));
        }catch(RuntimeException ignored) { }
    }
    private void kill() { Process p=process;process=null;if(p!=null)p.destroy(); }
    @Override public void close() {closed=true;kill();}
}
