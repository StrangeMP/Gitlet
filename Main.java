package gitlet;

import java.util.*;

/**
 * Driver class for Gitlet, a subset of the Git version-control system.
 *
 * @author StangeMP
 */
public class Main {
    private static void checkInitialized() {
        if (!Repository.GITLET_DIR.exists()) {
            Main.exit("Not in an initialized Gitlet directory.");
        }
    }

    public static void exit(String msg) {
        System.out.println(msg);
        System.exit(0);
    }

    private static final Map<String, List<Integer>> ARGSNUM = new HashMap<>(Map.ofEntries(
            Map.entry("init", List.of(1)),
            Map.entry("add", List.of(2)),
            Map.entry("commit", List.of(2)),
            Map.entry("rm", List.of(2)),
            Map.entry("log", List.of(1)),
            Map.entry("global-log", List.of(1)),
            Map.entry("find", List.of(2)),
            Map.entry("status", List.of(1)),
            Map.entry("checkout", List.of(2, 3, 4)),
            Map.entry("branch", List.of(2)),
            Map.entry("rm-branch", List.of(2)),
            Map.entry("reset", List.of(2)),
            Map.entry("merge", List.of(2))
    ));

    /**
     * Usage: java gitlet.Main ARGS, where ARGS contains
     * <COMMAND> <OPERAND1> <OPERAND2> ...
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            Main.exit("Please enter a command.");
        }
        String firstArg = args[0];
        if (!ARGSNUM.containsKey(firstArg)) {
            Main.exit("No command with that name exists.");
        }
        if (!ARGSNUM.get(firstArg).contains(args.length)) {
            Main.exit("Incorrect operands.");
        }

        Repository repo;
        if (firstArg.equals("init")) {
            repo = Repository.init();
            repo.save();
        } else {
            checkInitialized();
            Set<String> noLoad = new HashSet<>(Arrays.asList("log", "global-log"));
            if (noLoad.contains(firstArg)) {
                switch (firstArg) {
                    case "log":
                        Repository.log();
                        break;
                    case "global-log":
                        Repository.globalLog();
                        break;

                    default:
                        break;
                }

            } else {
                repo = Repository.load();
                switch (firstArg) {
                    case "add":
                        repo.add(args[1]);
                        break;
                    case "commit":
                        repo.commit(args[1]);
                        break;
                    case "rm":
                        repo.rm(args[1]);
                        break;
                    case "find":
                        List<String> ids = Repository.find(args[1]);
                        if (ids.isEmpty()) {
                            Main.exit("Found no commit with that message.");
                        }
                        for (String id : ids) {
                            System.out.println(id);
                        }
                        break;
                    case "status":
                        String statusStr = repo.status();
                        System.out.println(statusStr);
                        break;
                    case "checkout":
                        repo.checkout(args);
                        break;
                    case "branch":
                        repo.makeBranch(args[1]);
                        break;
                    case "rm-branch":
                        repo.removeBranch(args[1]);
                        break;
                    case "reset":
                        repo.reset(args[1]);
                        break;
                    case "merge":
                        repo.merge(args[1]);
                        break;
                    default:
                        break;
                }
                repo.save();
            }
        }

    }


}
