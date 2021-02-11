package me.minidigger.todoscan;

import java.util.Objects;
import java.util.StringJoiner;

public class Change {
    Todo oldTodo;
    Todo newTodo;
    boolean changedLine;
    boolean changedText;

    public Change(Todo oldTodo, Todo newTodo, boolean changedLine, boolean changedText) {
        this.oldTodo = oldTodo;
        this.newTodo = newTodo;
        this.changedLine = changedLine;
        this.changedText = changedText;
    }

    public Todo getOldTodo() {
        return oldTodo;
    }

    public Todo getNewTodo() {
        return newTodo;
    }

    public boolean isChangedLine() {
        return changedLine;
    }

    public boolean isChangedText() {
        return changedText;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Change change = (Change) o;
        return changedLine == change.changedLine && changedText == change.changedText && Objects.equals(oldTodo, change.oldTodo) && Objects.equals(newTodo, change.newTodo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(oldTodo, newTodo, changedLine, changedText);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Change.class.getSimpleName() + "[", "]")
                .add("oldTodo=" + oldTodo)
                .add("newTodo=" + newTodo)
                .add("changedLine=" + changedLine)
                .add("changedText=" + changedText)
                .toString();
    }
}
