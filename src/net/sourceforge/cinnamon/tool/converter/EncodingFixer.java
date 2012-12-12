package net.sourceforge.cinnamon.tool.converter;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedList;

// inspiration: http://stackoverflow.com/a/7743665, though changed heavily.

public class EncodingFixer extends FilterInputStream {

    LinkedList<Integer> inQueue = new LinkedList<Integer>();
    LinkedList<Integer> outQueue = new LinkedList<Integer>();
    LinkedList<Integer> output = new LinkedList<Integer>();
    final int[] search;
    final int[] replacement;

    public EncodingFixer(InputStream in, int[] search,
                         int[] replacement) {
        super(in);
        this.search = search;
        this.replacement = replacement;
    }

    private boolean isMatchFound() {
        if (inQueue.size() >= search.length) {
            Iterator<Integer> inIter = inQueue.iterator();
            for (int i : search) {
                if (i != inIter.next()) {
                    return false;
                }
            }
//            for (int i = 0; i < search.length; i++){
//                if (search[i] == inIter.next()){
//
//                }
//                else{
//                    return false;
//                }
//            }
        } else {
            return false;
        }
        System.out.println("*********************** Found match");
        return true;
    }

    private void readAhead() throws IOException {
        // Work up some look-ahead.
//        while (inQueue.size() < replacement.length) {
        while (inQueue.size() < search.length) {
            int next = super.read();
            inQueue.offer(next);
            if (next == -1)
                break;
        }
    }


    @Override
    public int read() throws IOException {

        // Next byte already determined.
        if (outQueue.isEmpty()) {

            readAhead();

            if (isMatchFound()) {
                for (int i : search) {
                    Integer replace = inQueue.remove();
//                    System.out.println("Found: " + replace);
                }
                for (int b : replacement) {
//                    System.out.println(" replace with: " + b);
                    outQueue.offer(b);
                }
//                for (int i = 0; i < search.length; i++){
//                    Integer replace = inQueue.remove();
//                    System.out.println("Found: " + replace);
//                }
//                for (int b : replacement){
//                    System.out.println(" replace with: " + b);
//                    outQueue.offer( b);
//                }
//                System.out.println();
            } else {
                Integer rem = inQueue.remove();
//                System.err.println("no match: "+rem);
                outQueue.add(rem);
            }
        }
        int out = outQueue.remove();
        output.add(out);
        return out;
    }

    public LinkedList<Integer> getOutput() {
        return output;
    }
}

