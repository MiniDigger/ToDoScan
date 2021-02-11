package me.minidigger.todoscan;

import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ToDoScan {

    public static void main(String[] args) throws IOException {
        String filePattern = envOrDefault("TODO_FILE_PATTERN", ".java;.vue;.scss;.js;.css;.md;.yml;.json;.ts;.html;.xml");
        // https://github.com/regexhq/todo-regex
        String pattern = envOrDefault("TODO_REGEX", "<!--[ \\t]*@?(?:todo|fixme):?[ \\t]*([^\\n]+)[ \\t]*-->|(?:@|\\/\\/[ \\t]*)?(?:todo|fixme):?[ \\t]*([^\\n]+)");
        String folder = envOrDefault("TODO_FOLDER", "D:\\IntellijProjects\\hangar2");
        String exclude =envOrDefault("TODO_EXCLUDE",  "node_modules;target;dist;.mvn;.idea;.nuxt;frontend-old;controllerold;daoold;serviceold");

        String statsFiles = envOrDefault("TODO_STATS_FILES", "src;frontend");
        String statsExtensions = envOrDefault("TODO_STATS_EXT", ".java;.vue;");

        String pat = envOrDefault("TODO_PAT", ""); // GITHUB_TOKEN secret
        String repo = envOrDefault("TODO_REPO", "PaperMC/Hangar"); // GITHUB_REPOSITORY env var
        String branch = envOrDefault("TODO_BRANCH", "nuxt-frontend");
        int issueId = Integer.parseInt(envOrDefault("", "335"));
        int prNumber = Integer.parseInt(envOrDefault("", "-1")); // PR_NUMBER=$(echo $GITHUB_REF | awk 'BEGIN { FS = "/" } ; { print $3 }')


        ToDoScan toDoScan = new ToDoScan();
        List<Todo> todos = toDoScan.scan(folder, pattern, filePattern, exclude);
        toDoScan.report(todos, statsFiles, statsExtensions);
        toDoScan.github(todos, pat, repo, branch, issueId, statsFiles, statsExtensions, prNumber);
    }

    public void report(List<Todo> todos, String statsFiles, String statsExtensions) {
        todos.forEach(System.out::println);

        System.out.println("Found: " + todos.size());
        long sum = 0;
        for (String prefix : statsFiles.split(";")) {
            long count = todos.stream().filter(todo -> todo.filePath.startsWith(prefix)).count();
            sum += count;
            System.out.println(prefix + ": " + count);
        }
        System.out.println("other: " + ((long) todos.size() - sum));

        sum = 0;
        for (String suffix : statsExtensions.split(";")) {
            long count = todos.stream().filter(todo -> todo.filePath.endsWith(suffix)).count();
            sum += count;
            System.out.println(suffix + ": " + count);
        }
        System.out.println("other: " + ((long) todos.size() - sum));
    }

    public void github(List<Todo> todos, String pat, String repoName, String branch, int issueId, String statsFiles, String statsExtensions, int prNumber) throws IOException {
        GitHub github = new GitHubBuilder().withOAuthToken(pat).build();

        GHRepository repo = github.getRepository(repoName);
        if (repo == null) {
            System.out.println("repo " + repoName + " not found");
            return;
        }
        GHIssue issue = repo.getIssue(issueId);
        if (issue == null) {
            System.out.println("issue " + repoName + "#" + issueId + " not found");
            return;
        }
        if (issue.getComments().size() < 2) {
            System.out.println("not enough comments!");
            return;
        }

        // collect old
        List<Todo> oldTodos = new ArrayList<>();
        for (String line : issue.getBody().split("\n")) {
            if (line.startsWith("-") && line.contains("<!--")) {
                String[] info = line.substring(line.lastIndexOf("<!--") + 4, line.lastIndexOf("-->")).split(";");
                oldTodos.add(new Todo(info[2], Integer.parseInt(info[1]), info[0]));
            }
        }

        // compare old against new
        List<Change> changes = new ArrayList<>();
        // first, check if all new are in old
        List<Todo> newTodos = new ArrayList<>();
        for (Todo todo : todos) {
            if (!oldTodos.contains(todo)) {
                boolean found = false;
                for (Todo oldTodo : oldTodos) {
                    // maybe just line changed?
                    if (oldTodo.getFilePath().equals(todo.getFilePath()) && oldTodo.getText().equals(todo.getText())) {
                        changes.add(new Change(oldTodo, todo, true, false));
                        found = true;
                        break;
                    }

                    // maybe just text changed?
                    if (oldTodo.getFilePath().equals(todo.getFilePath()) && oldTodo.getLine() == todo.getLine()) {
                        changes.add(new Change(oldTodo, todo, false, true));
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    newTodos.add(todo);
                }
            }
        }
        // then check if all old are in new
        List<Todo> removedTodos = new ArrayList<>();
        for (Todo oldTodo : oldTodos) {
            if (!todos.contains(oldTodo)) {
                boolean found = false;
                for (Todo todo : todos) {
                    // maybe just line changed?
                    if (todo.getFilePath().equals(oldTodo.getFilePath()) && todo.getText().equals(oldTodo.getText())) {
                        changes.add(new Change(oldTodo, todo, true, false));
                        found = true;
                        break;
                    }

                    // maybe just text changed?
                    if (todo.getFilePath().equals(oldTodo.getFilePath()) && todo.getLine() == oldTodo.getLine()) {
                        changes.add(new Change(oldTodo, todo, false, true));
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    removedTodos.add(oldTodo);
                }
            }
        }

        // todo remove duplicates from changes

        System.out.println("new todos: " + newTodos.size());
        newTodos.forEach(System.out::println);
        System.out.println("removed todos: " + removedTodos.size());
        removedTodos.forEach(System.out::println);
        System.out.println("changes: " + changes.size());
        changes.forEach(System.out::println);

        if (prNumber != -1) {
            GHPullRequest pullRequest = repo.getPullRequest(prNumber);
            if (pullRequest != null) {
                // TODO display new/removed/changes todos on PRs
                StringBuilder sb = new StringBuilder().append("# ToDo Update");
                boolean found = false;
                for (GHIssueComment comment : pullRequest.getComments()) {
                    if (comment.getBody().startsWith("# ToDo Update")) {
                        found = true;
                        comment.update(sb.toString());
                        break;
                    }
                }
                if (!found) {
                    pullRequest.comment(sb.toString());
                }
            } else {
                System.out.println("PR not found");
            }
        }

        // write current todos
        StringBuilder newIssue = new StringBuilder();
        newIssue.append("# Open ToDos").append("\n");
        for (Todo todo : todos) {
            writeTodo(repoName, branch, newIssue, todo, true, false);
        }

        issue.setBody(newIssue.toString());

        // handle removed todos
        GHIssueComment comment = issue.getComments().get(0);
        StringBuilder removedBuilder = new StringBuilder();
        removedBuilder.append(comment.getBody());
        for (Todo todo : removedTodos) {
            writeTodo(repoName, branch, removedBuilder, todo, false, true);
        }

        comment.update(removedBuilder.toString());

        // handle stats
        StringBuilder summary = new StringBuilder();
        summary.append("# Todo Summary (last updated ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append(")").append("\n");

        summary.append("Found: ").append(todos.size()).append("  \n");
        long sum = 0;
        summary.append("## Subproject stats").append("\n");
        for (String prefix : statsFiles.split(";")) {
            long count = todos.stream().filter(todo -> todo.filePath.startsWith(prefix)).count();
            sum += count;
            summary.append(prefix).append(": ").append(count).append("  \n");
        }
        summary.append("other: ").append((long) todos.size() - sum).append("  \n");

        sum = 0;
        summary.append("## File stats").append("\n");
        for (String suffix : statsExtensions.split(";")) {
            long count = todos.stream().filter(todo -> todo.filePath.endsWith(suffix)).count();
            sum += count;
            summary.append(suffix).append(": ").append(count).append("  \n");
        }
        summary.append("other: ").append((long) todos.size() - sum).append("  \n");

        issue.getComments().get(1).update(summary.toString());
    }

    private void writeTodo(String repoName, String branch, StringBuilder builder, Todo todo, boolean includeComment, boolean done) {
        String url = buildUrl(repoName, branch, todo.getFilePath(), todo.getLine(), todo.getFileName() + "#" + todo.getLine(), done);
        if (done) {
            builder.append("- [x] '");
        } else {
            builder.append("- [ ] '");
        }
        builder.append(todo.getText()).append("' in ").append(url);
        if (includeComment) {
            builder.append(" <!--").append(todo.getFilePath()).append(";").append(todo.getLine()).append(";").append(todo.text).append("-->");
        }
        builder.append("  \n");
    }

    private String buildUrl(String repo, String branch, String file, int line, String text, boolean historic) {
        // TODO if historic = true, make the link point to old stuff
        return "[" + text + "](https://github.com/" + repo + "/blob/" + branch + "/" + file + "#L" + line + ")";
    }

    public List<Todo> scan(String inFolder, String inToDoPattern, String inFilePattern, String inExcludes) throws IOException {
        Path root = Path.of(inFolder);
        Set<String> excludes = Set.of(inExcludes.split(";"));
        Set<String> filePattern = Set.of(inFilePattern.split(";"));
        Pattern toDoPattern = Pattern.compile(inToDoPattern, Pattern.CASE_INSENSITIVE);

        Set<Todo> todos = new HashSet<>();

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (excludes.contains(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String fileName = file.getFileName().toString();
                int idx = fileName.lastIndexOf(".");
                if (idx != -1) {
                    String ext = fileName.substring(idx);
                    if (filePattern.contains(ext)) {
                        scan(file, toDoPattern, todos);
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });

        return todos.stream()
                .map(todo -> todo.relativize(root))
                .sorted(Comparator.comparing(Todo::getFilePath).thenComparing(Todo::getLine))
                .collect(Collectors.toList());
    }

    public void scan(Path file, Pattern pattern, Set<Todo> todos) throws IOException {
        List<String> lines = Files.readAllLines(file);
        for (int i = 1; i <= lines.size(); i++) {
            String line = lines.get(i - 1);
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                String todo = matcher.group(1);
                if (todo == null) {
                    todo = matcher.group(2);
                }
                todos.add(new Todo(todo.trim(), i, file));
            }
        }
    }

    private static String envOrDefault(String env, String defaultVal) {
        String val = System.getenv(env);
        if (val == null) {
            return defaultVal;
        } else {
            return val;
        }
    }
}
