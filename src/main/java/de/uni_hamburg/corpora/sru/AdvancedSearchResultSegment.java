/**
 * @file DBSearchResult.java
 * @copyright Hamburger Zentrum für Sprach Korpora.
 */

package de.uni_hamburg.corpora.hzsksru;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.net.URI;

/**
 * A segment in FCS advanced search.
 * This is HZSK version of segment, so it has starts and ends for different
 * timelines and stuff.
 * @author tpirinen
 */
public class AdvancedSearchResultSegment 
        implements Comparable<AdvancedSearchResultSegment> {

    private String annotation;
    private String text;
    private boolean hitness;
    private int start;
    private int end;

    public AdvancedSearchResultSegment(String ann) {
        this.annotation = ann;
        this.start = -1;
        this.end = -2;
    }

    public AdvancedSearchResultSegment(String ann, boolean highlight) {
        this.annotation = ann;
        this.hitness = true;
        this.start = -1;
        this.end = -2;
    }
    public AdvancedSearchResultSegment(String ann, String text,
            int start, int end) {
        this.annotation = ann;
        this.text = text;
        this.start = start;
        this.end = end;
        this.hitness = false;
    }

    public AdvancedSearchResultSegment(String text, int start, int end) {
        this.annotation = text;
        this.text = text;
        this.start = start;
        this.end = end;
        this.hitness = false;
    }

    public String getAnnotation() {
        return annotation;
    }

    public String getText() {
        return text;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public void setStart(int start) {
        this.start = start;
    }

    public void setEnd(int end) {
        this.end = end;
    }
    
    public boolean isHighlighted() {
        return hitness;
    }
    
    public void setHighlighted(boolean highlighted) {
        hitness = highlighted;
    }

    @Override
    public int compareTo(AdvancedSearchResultSegment rhs) {
        if (this.getStart() == rhs.getStart()) {
            if (this.getEnd() == rhs.getEnd()) {
                if (this.getAnnotation().equals(rhs.getAnnotation())) {
                    return this.getText().compareTo(rhs.getText());
                } else {
                    return 
                        this.getAnnotation().compareTo(rhs.getAnnotation());
                }
            } else {
                return this.getEnd() - rhs.getEnd();
            }
        } else {
            return this.getStart() - rhs.getStart();
        }
    }
}

