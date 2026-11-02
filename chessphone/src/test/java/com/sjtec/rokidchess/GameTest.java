package com.sjtec.rokidchess;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import com.github.bhlangonijr.chesslib.*;
import com.github.bhlangonijr.chesslib.move.Move;

public class GameTest {
    private long perft(Board b,int depth){if(depth==0)return 1;long count=0;for(Move m:b.legalMoves()){b.doMove(m);count+=perft(b,depth-1);b.undoMove();}return count;}
    @Test public void startingPositionPerft(){assertEquals(8902,perft(new Board(),3));}
    @Test public void illegalMoveIsAtomic(){Game g=new Game();try{g.move("e2e5");fail();}catch(IllegalArgumentException expected){}assertEquals(Game.START,g.board.getFen());assertTrue(g.moves.isEmpty());}
    @Test public void invalidFenIsAtomic(){Game g=new Game();for(String f:List.of("8/8/8/8/8/8/8/8 w - - 0 1","7k/8/8/8/8/8/8/6KK w - - 0 1","7k/8/8/8/8/8/6K1/7P w - - 0 1")){
        try{g.load(f,List.of());fail(f);}catch(IllegalArgumentException expected){}assertEquals(Game.START,g.board.getFen());}}
    @Test public void castling(){Game g=new Game();g.load("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1",List.of("e1g1"));assertEquals(Piece.WHITE_ROOK,g.board.getPiece(Square.F1));assertEquals(Piece.WHITE_KING,g.board.getPiece(Square.G1));}
    @Test public void enPassant(){Game g=new Game();g.load(Game.START,List.of("e2e4","a7a6","e4e5","d7d5","e5d6"));assertEquals(Piece.NONE,g.board.getPiece(Square.D5));assertEquals(Piece.WHITE_PAWN,g.board.getPiece(Square.D6));}
    @Test public void underPromotion(){Game g=new Game();g.load("7k/P7/8/8/8/8/8/7K w - - 0 1",List.of("a7a8n"));assertEquals(Piece.WHITE_KNIGHT,g.board.getPiece(Square.A8));}
    @Test public void mateAndUndo(){Game g=new Game();g.load(Game.START,List.of("f2f3","e7e5","g2g4","d8h4"));assertNotNull(Game.terminal(g.board));assertTrue(g.legal().isEmpty());g.undo();assertNull(Game.terminal(g.board));assertEquals(3,g.moves.size());}
    @Test public void repetitionDoesNotAutoClaimThreefold(){Game g=new Game();g.load(Game.START,List.of("g1f3","g8f6","f3g1","f6g8","g1f3","g8f6","f3g1","f6g8"));assertTrue(g.board.isRepetition(3));assertNull(Game.terminal(g.board));}
}
