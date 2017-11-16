package src.main.extraction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String[] args) throws Exception {
        ExtractionMeta meta = new ExtractionMeta("MyVoldemort",501,800);
        meta.loadProperty("database.properties");
        double ratio=meta.countRatio();
        System.out.println(ratio);
    }
}
