package DBMS;

import java.util.Map;
import java.io.Serializable;
import java.util.HashMap;

public class BitmapIndex implements Serializable {
    private String tablename;
    private String columnName;
    private Map<String, String> b;

    public BitmapIndex(String tablename, String columnName) {
        this.tablename = tablename;
        this.columnName = columnName;
        b = new HashMap<>();
    }

    public String getTablename() {
        return tablename;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setTablename(String tablename) {
        this.tablename = tablename;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public Map<String, String> getB() {
        return b;
    }

    public void setB(Map<String, String> b) {
        this.b = b;
    }

}
