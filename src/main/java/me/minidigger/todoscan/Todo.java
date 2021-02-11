package me.minidigger.todoscan;

import java.nio.file.Path;
import java.util.Objects;
import java.util.StringJoiner;

public class Todo {
    String text;
    int line;
    Path file;
    String filePath;
    String fileName;

    public Todo(String text, int line, Path file) {
        this.text = text.replace(";", ",");
        this.line = line;
        this.file = file;
    }

    public Todo(String text, int line, String file) {
        this.text = text.replace(";", ",");
        this.line = line;
        this.filePath = file;
        this.fileName = file.substring(file.lastIndexOf("/") + 1);
    }

    public Todo relativize(Path parent) {
        filePath = parent.relativize(file).toString();
        filePath = filePath.replace("\\", "/");
        fileName = file.getFileName().toString();
        return this;
    }

    public String getText() {
        return text;
    }

    public int getLine() {
        return line;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getFileName() {
        return fileName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Todo todo = (Todo) o;
        return line == todo.line && Objects.equals(text, todo.text) && Objects.equals(filePath, todo.filePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, line, filePath);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Todo.class.getSimpleName() + "[", "]")
                .add("find='" + text + "'")
                .add("line=" + line)
                .add("filePath=" + filePath)
                .toString();
    }
}
