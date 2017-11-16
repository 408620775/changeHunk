package src.main.extraction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String[] args) throws Exception {
        ExtractionMeta meta = new ExtractionMeta("MyFlink",1001,1300);
        meta.loadProperty("database.properties");
        meta.getMetaTableData();
        meta.just_in_time("/home/niu/test/flink");
    }
}
