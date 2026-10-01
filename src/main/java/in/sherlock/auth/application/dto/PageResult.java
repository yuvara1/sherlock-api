package in.sherlock.auth.application.dto;

import java.util.List;
import java.util.function.Function;

public record PageResult<T>(List<T> data, long total, int page, int pageSize, boolean hasMore) {
    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(data.stream().map(mapper).toList(), total, page, pageSize, hasMore);
    }
}
