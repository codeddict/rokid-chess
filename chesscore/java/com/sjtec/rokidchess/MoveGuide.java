package com.sjtec.rokidchess;

import com.github.bhlangonijr.chesslib.*;
import com.github.bhlangonijr.chesslib.move.Move;
import java.util.Map;

/** Instructions refer to physical pieces, with directions seen from the selected player's side. */
final class MoveGuide {
    static final Map<String,String> NAMES=Map.of("PAWN","폰","KNIGHT","나이트","BISHOP","비숍","ROOK","룩","QUEEN","퀸","KING","킹");
    static String describe(Board board,Move move,String player){
        String uci=move.toString(),from=uci.substring(0,2),to=uci.substring(2,4);
        Piece piece=board.getPiece(move.getFrom()),target=board.getPiece(move.getTo());
        String name=NAMES.getOrDefault(piece.getPieceType().name(),"말");
        String first="내 "+name+" "+from+" → "+to;
        if(piece.getPieceType()==PieceType.KING&&Math.abs(from.charAt(0)-to.charAt(0))==2){
            boolean kingSide=to.charAt(0)>from.charAt(0);char rank=from.charAt(1);
            return first+"\n룩 "+(kingSide?"h":"a")+rank+" → "+(kingSide?"f":"d")+rank+"도 옮기세요.";
        }
        int sign=player.equals("white")?1:-1;
        int forward=(to.charAt(1)-from.charAt(1))*sign,right=(to.charAt(0)-from.charAt(0))*sign;
        String direction=forward==0?"":(forward>0?"앞으로 ":"뒤로 ")+Math.abs(forward)+"칸";
        if(right!=0)direction+=(direction.isEmpty()?"":" · ")+(right>0?"오른쪽 ":"왼쪽 ")+Math.abs(right)+"칸";
        String result=first+"\n"+direction+" 옮기세요.";
        if(piece.getPieceType()==PieceType.PAWN&&from.charAt(0)!=to.charAt(0)&&target==Piece.NONE)
            result+="\n앙파상: "+to.charAt(0)+from.charAt(1)+"의 상대 폰을 제거하세요.";
        else if(target!=Piece.NONE){String captured=NAMES.get(target.getPieceType().name());result+="\n도착 칸의 상대 "+captured+(finalConsonant(captured)?"을":"를")+" 잡으세요.";}
        if(move.getPromotion()!=Piece.NONE){String promoted=NAMES.get(move.getPromotion().getPieceType().name());result+="\n폰을 "+promoted+(finalConsonant(promoted)?"으로":"로")+" 바꾸세요.";}
        return result;
    }
    private static boolean finalConsonant(String text){return (text.charAt(text.length()-1)-0xAC00)%28!=0;}
}
