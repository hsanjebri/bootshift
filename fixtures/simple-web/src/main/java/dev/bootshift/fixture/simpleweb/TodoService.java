package dev.bootshift.fixture.simpleweb;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

@Service
public class TodoService {

    private final Map<Long, Todo> todos = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    public List<Todo> findAll() {
        return todos.values().stream().sorted(Comparator.comparing(Todo::id)).toList();
    }

    public Optional<Todo> findById(long id) {
        return Optional.ofNullable(todos.get(id));
    }

    public Todo create(Todo todo) {
        long id = ids.incrementAndGet();
        Todo saved = new Todo(id, todo.title(), todo.done());
        todos.put(id, saved);
        return saved;
    }
}
