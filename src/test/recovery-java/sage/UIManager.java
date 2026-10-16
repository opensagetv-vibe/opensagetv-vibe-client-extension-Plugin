package sage;

/** Only the stock per-UI identity boundary is shadowed in this isolated JVM. */
public final class UIManager {
    private final String name;
    public UIManager(String name) { this.name = name; }
    public String getLocalUIClientName() { return name; }
}
