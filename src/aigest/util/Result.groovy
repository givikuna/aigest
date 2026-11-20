package aigest.util

import groovy.transform.CompileStatic

@CompileStatic
abstract class Result<T, E> {

    private Result() {}

    static <T, E> Result<T, e> ok(T value) {
        new Ok<T, E>(value)
    }

    static <T, E> Result<T, E> err(E error) {
        new Err<T, E>(error)
    }

    static <T> Result<T, String> trying(String context, Closure<T> action) {
        try {
            return Result.<T, String>ok(action.call())
        } catch (Exception ex) {
            String detail = ex.message ?: ex.class.simpleName
            return Result.<T, String>err("${context}: ${detail}")
        }
    }

    static <A, B, R, E> Result<R, E> zip(
        Result<A, E> left,
        Result<B, E> right,
        Closure<R> combine
    ) {
        left.flatMap { A a ->
            right.map { B b ->
                combine.call(a, b)
            }
        }
    }

    static <T, E> Result<List<T>, E> sequence(List<Result<T, E>> results) {
        List<T> acc = new ArrayList<T>(results.size())

        for (Result<T, E> item : results) {
            if (item instanceof Err) {
                return err(((Err<T, E>) item).error)
            }

            acc.add(((Ok<T, E>) item).value)
        }

        ok(Collections.unmodifiableList(acc))
    }

    abstract boolean isOk()

    boolean isErr() {
        !isOk()
    }

    abstract <U> Result<U, E> map(Closure<U> fn)

    abstract <U> Result<U, E> flatMap(Closure<Result<U, E>> fn)

    abstract <F> Result<T, F> mapError(Closure<F> fn)

    abstract <R> R fold(Closure<R> onErr, Closure<R> onOk)

    abstract T orElse(T fallback)

    static final class Ok<T, E> extends Result<T, E> {

        final T value

        Ok(T value) {
            this.value = value
        }

        @Override
        boolean isOk() {
            true
        }

        @Override
        <U> Result<U, E> map(Closure<U> fn) {
            ok(fn.call(value))
        }

        @Override
        <U> Result<U, E> flatMap(Closure<Result<U, E>> fn) {
            fn.call(value)
        }

        @Override
        <R> R fold(Closure<R> onErr, Closure<R>, onOk) {
            onOk.call(value)
        }

        @Override
        <F> Result<T, F> mapError(Closure<F> fn) {
            ok(value)
        }

        @Override
        T orElse(T fallback) {
            value
        }

    }

    static final class Err<T, E> extends Result<T, E> {

        final E error

        Err(E error) {
            this.error = error
        }

        @Override
        boolean isOk() {
            false
        }

        @Override
        <U> Result<U, E> map(Closure<U> fn) {
            err(error)
        }

        @Override
        <U> Result<U, E> flatMap(Closure<Result<U, E>> fn) {
            err(error)
        }

        @Override
        <F> Result<T, F> mapError(Closure<F> fn) {
            err(fn.call(error))
        }

        @Override
        <R> R fold(Closure<R> onErr, Closure<R> onOk) {
            onErr.call(error)
        }

        @Override
        T orElse(T fallback) {
            fallback
        }

    }

}
