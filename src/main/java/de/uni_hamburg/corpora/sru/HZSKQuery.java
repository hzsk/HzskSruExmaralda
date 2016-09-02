/**
 * @file HZSKQuery.java
 * @copyright Hamburger Zentrum für Korpora
 *
 */
package de.uni_hamburg.corpora.hzsksru;

import java.util.List;
import java.util.ArrayList;

/**
 * Relatively simple class for a parsed CQL/FCS query for HZSK.
 * I made it to act between search engine and its backends a bit, but it's
 * organically hacked together right now. Partially cause directly using CQL
 * AST's in DB connections or stuff didn't seem nice.
 * No it won't support multiple boolean operators on layers with arbitrary
 * nesting, you can have a query term per layer with an operator.
 * It'd be possible to support infinite layers with infinite queries but this
 * is purposedly under-engineered copy-pasta code.
 */
public class HZSKQuery {
    // just one combotype over all searches
    enum ComboType {WHATEVER, AND, OR, NEAR};
    protected ComboType combine = ComboType.WHATEVER;
    protected List<String> textSearch;
    protected List<String> posSearch;
    protected List<String> lemmaSearch;
    private boolean regexSearch = false;
    private boolean usable = false;

    public HZSKQuery() {
        this.usable = false;
        textSearch = new ArrayList<String>();
        posSearch = new ArrayList<String>();
        lemmaSearch = new ArrayList<String>();
    }

    public HZSKQuery(String textSearch) {
        this.textSearch = new ArrayList<String>();
        posSearch = new ArrayList<String>();
        lemmaSearch = new ArrayList<String>();
        this.textSearch.add(textSearch);
        this.usable = true;
    }

    public HZSKQuery(String layer, String layerSearch) {
        textSearch = new ArrayList<String>();
        posSearch = new ArrayList<String>();
        lemmaSearch = new ArrayList<String>();
        if (layer.equals("pos")) {
            this.posSearch.add(layerSearch);
            this.usable = true;
        } else if (layer.equals("lemma")) {
            this.lemmaSearch.add(layerSearch);
            this.usable = true;
        } else if (layer.equals("text")) {
            this.textSearch.add(layerSearch);
            this.usable = true;
        } else {
            this.usable = false;
        }
    }

    // @fixme there's no sanity checks here just overwriting
    public HZSKQuery(HZSKQuery lhs, HZSKQuery rhs, String op) {
        textSearch = new ArrayList<String>(lhs.textSearch);
        posSearch = new ArrayList<String>(lhs.posSearch);
        lemmaSearch = new ArrayList<String>(lhs.lemmaSearch);
        textSearch.addAll(rhs.textSearch);
        posSearch.addAll(rhs.posSearch);
        lemmaSearch.addAll(rhs.lemmaSearch);
        if (op.equals("and")) {
            combine = ComboType.AND;
        } else if (op.equals("or")) {
            combine = ComboType.OR;
        } else if (op.equals("near")) {
            combine = ComboType.NEAR;
        } else {
            combine = ComboType.WHATEVER;
        }
        usable = true;
    }

    public boolean isComplex() {
        if (combine != ComboType.WHATEVER) {
            return true;
        }
        if ((hasTextSearch() && (hasPosSearch() || hasLemmaSearch()))) {
            // TEXT plus something
            return true;
        }
        if ((textSearch.size() > 1) || (lemmaSearch.size() > 1) || 
            (posSearch.size() > 1)) {
            return true;
        }
        return false;
    }

    public boolean hasTextSearch() {
        if (this.usable) {
            return this.textSearch.size() > 0;
        } else {
            return false;
        }
    }

    public boolean hasNonTextSearch() {
        if (this.usable) {
            return hasPosSearch() || hasLemmaSearch();
        } else {
            return false;
        }
    }

    public boolean hasPosSearch() {
        if (this.usable) {
            return this.posSearch.size() > 0;
        } else {
            return false;
        }
    }

    public boolean hasLemmaSearch() {
        if (usable) {
            return lemmaSearch.size() > 0;
        } else {
            return false;
        }
    }
    public void setTextSearch(String search) {
        textSearch.clear();
        textSearch.add(search);
        usable = true;
    }

    public void setPosSearch(String search) {
        posSearch.clear();
        posSearch.add(search);
        usable = true;
    }

    public void setLemmaSearch(String search) {
        lemmaSearch.clear();
        lemmaSearch.add(search);
        usable = true;
    }

    public boolean isUsable() {
        if (!usable) {
            return false;
        }
        if (!hasTextSearch() && !hasLemmaSearch() && !hasPosSearch()) {
            return false;
        }
        return true;
    }

    public String getTextSearch() {
        return textSearch.get(0);
    }

    public List<String> getTextSearches() {
        return textSearch;
    }

    public String getPosSearch() {
        return posSearch.get(0);
    }

    public List<String> getPosSearches() {
        return posSearch;
    }

    public String getLemmaSearch() {
        return lemmaSearch.get(0);
    }

    public List<String> getLemmaSearches() {
        return lemmaSearch;
    }

    public HZSKQuery.ComboType getCombinator() {
        return combine;
    }
}
