package gitlet;

import java.io.File;
import java.io.Serializable;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Represents a gitlet commit object.
 *
 * @author StrangeMP
 */
public class Commit implements Serializable, Comparable<Commit> {
    final Date timeStamp;
    /**
     * The message of this Commit.
     */
    final String message;

    final Commit[] parents;

    final String sha1;

    HashMap<File, Blob> content;

    Commit(String msg, Commit[] P) {
        message = msg;
        this.parents = P[0] == null ? new Commit[0] : P;
        timeStamp = this.parents.length == 0 ? new Date(0) : new Date();
        sha1 = Utils.sha1(timeStamp.toString(), message);
        if (this.parents.length != 0) {
            Commit head = Repository.getHead();
            content = (HashMap<File, Blob>) head.content.clone();
            HashMap<File, StageObject> stage = Repository.getStage();
            // throws ConcurrentModificationException
            // for (File f : stage.keySet()) {
            //     if (stage.get(f) == null) {
            //         content.remove(f);
            //     } else {
            //         content.put(f, stage.get(f).blob);
            //     }
            //     stage.remove(f);
            // }
            for (var entryIterator = stage.entrySet().iterator(); entryIterator.hasNext(); ) {
                var entry = entryIterator.next();
                var f = entry.getKey();
                if (stage.get(f) == null) {
                    content.remove(f);
                } else {
                    content.put(f, stage.get(f).blob);
                }
                entryIterator.remove();
            }
        } else {
            content = new HashMap<>();
        }
    }

    @Override
    public int compareTo(Commit o) {
        return this.sha1.compareTo(o.sha1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        if (o.getClass() != this.getClass()) {
            return false;
        }
        return this.sha1.equals(((Commit) o).sha1);
    }

    public void restore() {
        for (Blob blob : content.values()) {
            blob.checkout();
        }
    }

    public boolean restore(File f) {
        Blob b = content.get(f);
        if (b == null) {
            return false;
        }
        b.checkout();
        return true;
    }

    public boolean restore(String fileName) {
        return restore(Utils.join(Repository.CWD, fileName));
    }

    public Commit findLatestCommonAncestor(Commit commit) {
        Set<Commit> possibleAncestors = new HashSet<>();
        dfs(commit, possibleAncestors::add, (Commit cmt) -> cmt.parents.length == 0);
        Commit[] ret = new Commit[]{null};
        dfs(this, (Commit cmt) -> {
            if (possibleAncestors.contains(cmt)) {
                ret[0] = cmt;
            }
        }, (Commit cmt) -> ret[0] != null);
        return ret[0];
    }

    private void dfs(Commit operand, Consumer<Commit> op, Function<Commit, Boolean> checkQuit) {
        op.accept(operand);
        if (checkQuit.apply(operand)) {
            return;
        }
        for (Commit cmt : operand.parents) {
            dfs(cmt, op, checkQuit);
        }
    }

    public boolean tracks(File f) {
        return content.containsKey(f);
    }

    public boolean hasIdenticalFile(File f) {
        return tracks(f) && content.get(f).sha1.equals(Repository.sha1OfFile(f));
    }

    public boolean hasBlob(Blob b) {
        return content.containsValue(b);
    }

    @Override
    public int hashCode() {
        return sha1.hashCode();
    }

}
