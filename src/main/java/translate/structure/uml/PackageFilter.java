package translate.structure.uml;

import java.util.ArrayList;
import java.util.List;

public class PackageFilter {
    private final List<String> blacklist = new ArrayList<>();
    private final List<String> whitelist = new ArrayList<>();

    public void add2Bl(String entry) {
        blacklist.add(entry);
    }

    public void add2Wl(String entry) {
        whitelist.add(entry);
    }

    public boolean isValid(String entry) {
        boolean inWhitelist = false;
        for (var wl : whitelist) {
            inWhitelist = entry.startsWith(wl);
            if (inWhitelist) break;
        }

        boolean emptyWhitelist = whitelist.isEmpty();
        if (!inWhitelist && !emptyWhitelist) return false;

        for (var bl : blacklist) {
            if (entry.startsWith(bl)) {
                return false;
            }
        }

        if (emptyWhitelist) return true;

        return inWhitelist;
    }
}
