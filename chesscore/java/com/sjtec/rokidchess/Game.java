package com.sjtec.rokidchess;

import com.github.bhlangonijr.chesslib.*;
import com.github.bhlangonijr.chesslib.move.Move;
import java.util.*;

/** The recorded position is authoritative; vision can only propose its legal successor. */
final class Game {
    static final String START="rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    Board board=new Board();
    String root=START;
    final ArrayList<String> moves=new ArrayList<>();
    void load(String fen,List<String> history) {
        validateFen(fen);
        fen=fen.trim().replaceAll("\\s+"," ");
        Board candidate=new Board(); candidate.loadFromFen(fen);
        for(String uci:history) {
            Move m=find(candidate,uci);
            if(terminal(candidate)!=null || m==null || !candidate.doMove(m,true)) throw new IllegalArgumentException("불가능한 수: "+uci);
        }
        root=fen; moves.clear(); moves.addAll(history); board=candidate;
    }
    void move(String uci) {
        Move move=find(board,uci);
        if(terminal(board)!=null || move==null || !board.doMove(move,true)) throw new IllegalArgumentException("불가능한 수: "+uci);
        moves.add(move.toString());
    }
    void undo() { if(!moves.isEmpty()) load(root,new ArrayList<>(moves.subList(0,moves.size()-1))); }
    static Move find(Board b,String uci) {
        for(Move m:b.legalMoves()) if(m.toString().equals(uci)) return m; return null;
    }
    List<String> legal() {
        ArrayList<String> result=new ArrayList<>();
        if(terminal(board)==null) for(Move m:board.legalMoves()) result.add(m.toString());
        return result;
    }
    static String terminal(Board b) {
        if(b.isMated()) return b.getSideToMove()==Side.WHITE?"체크메이트 · 흑 승":"체크메이트 · 백 승";
        if(b.isStaleMate()) return "스테일메이트 · 무승부";
        if(b.isInsufficientMaterial()) return "기물 부족 · 무승부";
        if(b.isRepetition(5) || b.getHalfMoveCounter()>=150) return "반복/75수 · 무승부";
        return null;
    }
    static char[] pieces(Board board) {
        char[] result=new char[64];
        for(int i=0;i<64;i++) {
            int rank=7-i/8,file=i%8;
            Piece p=board.getPiece(Square.squareAt(rank*8+file));
            result[i]=p==Piece.NONE?'.':p.getFenSymbol().charAt(0);
        }
        return result;
    }
    static void validateFen(String fen) {
        if(fen==null) throw new IllegalArgumentException("FEN이 필요합니다.");
        String[] f=fen.trim().split("\\s+");
        if(f.length!=6 || !f[1].matches("w|b") || !f[2].matches("-|K?Q?k?q?") || f[2].isEmpty()
            || !f[3].matches("-|[a-h][36]") || !f[4].matches("[0-9]{1,5}") || !f[5].matches("[0-9]{1,5}"))
            throw new IllegalArgumentException("전체 FEN 6개 필드를 확인하세요.");
        String[] rows=f[0].split("/"); int white=0,black=0;
        if(rows.length!=8) throw new IllegalArgumentException("FEN은 8행이어야 합니다.");
        for(int row=0;row<8;row++) {
            int cells=0;
            for(char c:rows[row].toCharArray()) {
                if(c>='1'&&c<='8') cells+=c-'0';
                else if("prnbqkPRNBQK".indexOf(c)>=0) {
                    cells++; if(c=='K')white++; if(c=='k')black++;
                    if((row==0||row==7) && (c=='p'||c=='P')) throw new IllegalArgumentException("폰은 끝 행에 있을 수 없습니다.");
                } else throw new IllegalArgumentException("FEN 기물 오류");
            }
            if(cells!=8) throw new IllegalArgumentException("FEN 행은 8칸이어야 합니다.");
        }
        if(white!=1||black!=1||Integer.parseInt(f[5])<1) throw new IllegalArgumentException("양쪽 왕과 수 번호를 확인하세요.");
        Board b=new Board(); b.loadFromFen(fen);
        int k=b.getKingSquare(Side.WHITE).ordinal(), other=b.getKingSquare(Side.BLACK).ordinal();
        if(Math.max(Math.abs(k%8-other%8),Math.abs(k/8-other/8))<=1) throw new IllegalArgumentException("왕이 인접해 있습니다.");
        Board previous=b.clone(); previous.setSideToMove(b.getSideToMove().flip());
        if(previous.isKingAttacked()) throw new IllegalArgumentException("차례가 아닌 쪽의 왕이 체크입니다.");
    }
}
