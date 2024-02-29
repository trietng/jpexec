package org.trietng;

import java.io.*;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JavaParallelExecutor {
    private enum ConsoleBackgroundColor {

        RESET("\033[0m"),
        BLACK("\033[40m"),   // BLACK
        RED("\033[41m"),     // RED
        GREEN("\033[42m"),   // GREEN
        YELLOW("\033[43m"),  // YELLOW
        BLUE("\033[44m"),    // BLUE
        MAGENTA("\033[45m"), // MAGENTA
        CYAN("\033[46m"),    // CYAN
        WHITE("\033[47m");

        private final String value;

        ConsoleBackgroundColor(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    private static class CommandExecutor implements Runnable {
        private final ConsoleBackgroundColor backgroundColor;
        private final String prefix;
        private final String command;

        private Process process;

        public CommandExecutor(String backgroundColor, String prefix, String command) {
            this.backgroundColor = ConsoleBackgroundColor.valueOf(backgroundColor);
            this.prefix = prefix;
            this.command = command;
            this.process = null;
        }

        public void destroy() {
            this.process.descendants().forEach(ProcessHandle::destroy);
            this.process.destroy();
        }

        public void run() {
            try {
                var processBuilder = new ProcessBuilder(command.split(" "));
                processBuilder.redirectErrorStream(true);
                process = processBuilder.start();
                var reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                System.out.print(ConsoleBackgroundColor.RESET);
                while ((line = reader.readLine()) != null) {
                    System.out.println(backgroundColor + prefix + ConsoleBackgroundColor.RESET + " " + line);
                }
                process.waitFor();
                this.destroy();
            } catch (IOException | InterruptedException e) {
                System.err.println(e.getMessage());
            }
        }
    }

    private static final String MESSAGE_HELP = "Usage: ./jpexec \"<background-color>;<prefix>;<command>\" ...";
    private static final String MESSAGE_COMMANDS_LIMIT = "Only support up to 4 commands";
    private static final String MESSAGE_SHUTTING_DOWN = "Shutting down...";
    private static final String MESSAGE_INVALID_COMMAND = "Invalid command format: ";
    private static final String MESSAGE_INVALID_BACKGROUND_COLOR = "Invalid background color: ";

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println(MESSAGE_HELP);
        }
        else if (args.length > 4) {
            System.out.println(MESSAGE_COMMANDS_LIMIT);
        }
        else if (Objects.equals(args[0], "help") || Objects.equals(args[0], "--help") || Objects.equals(args[0], "-h")) {
            System.out.println(MESSAGE_HELP);
        }
        else {
            String[][] commands = new String[args.length][3];
            for (int i = 0; i < args.length; i++) {
                var temp = args[i].split(";");
                if (temp.length != 3) {
                    System.out.print(MESSAGE_INVALID_COMMAND);
                    System.out.println(args[i]);
                    System.out.println(MESSAGE_HELP);
                    return;
                }
                commands[i] = temp;
            }
            ExecutorService executor = Executors.newFixedThreadPool(4);
            CommandExecutor[] ces = new CommandExecutor[args.length];
            for (var i = 0; i < args.length; i++) {
                try {
                    ces[i] = new CommandExecutor(commands[i][0], commands[i][1], commands[i][2]);
                }
                catch (IllegalArgumentException e) {
                    System.out.print(MESSAGE_INVALID_BACKGROUND_COLOR);
                    System.out.println(commands[i][0]);
                    System.out.print("Available background colors: ");
                    for (var BGC : ConsoleBackgroundColor.values()) {
                        System.out.print(BGC + BGC.name() + ConsoleBackgroundColor.RESET + " ");
                    }
                    System.out.println();
                    return;
                }
            }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println(MESSAGE_SHUTTING_DOWN);
                for (var ce : ces) {
                    ce.destroy();
                }
            }));
            for (var ce : ces) {
                executor.execute(ce);
            }
            executor.shutdown();
        }
    }
}