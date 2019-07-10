package opendial.modules.mumedefault.information;

public class Pair<T, U> {
    private T first;
    private U second;

    public Pair(T f, U s) {
        this.first = f;
        this.second = s;
    }

    public T getFirst() {
        return first;
    }

    public U getSecond() {
        return second;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Pair)) return false;

        Pair<?, ?> pair = (Pair<?, ?>) o;

        if (!getFirst().equals(pair.getFirst())) return false;
        return getSecond().equals(pair.getSecond());

    }

    @Override
    public int hashCode() {
        int result = getFirst().hashCode();
        result = 31 * result + getSecond().hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Pair{" +
                "first=" + first +
                ", second=" + second +
                '}';
    }
}
