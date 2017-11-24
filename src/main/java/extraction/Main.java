package extraction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String[] args) throws Exception {
        ExtractionMeta meta = new ExtractionMeta("MyFlink",1001,1300);
        meta.loadProperty("database.properties");
        double ratio=meta.countRatio();
        System.out.println(ratio);
    }
}