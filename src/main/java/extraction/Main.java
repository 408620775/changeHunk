package extraction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String[] args) throws Exception {
        ExtractionMeta extractionMeta = new ExtractionMeta("MyFlink",1001,1300);
        extractionMeta.getMetaTableData();
        extractionMeta.just_in_time("/home/niubinbin/test/flink");
//        ExtractionMetrics extractionMetrics = new ExtractionMetrics("MyVoldemort",501,800);
//        extractionMetrics.extraFromTxt("/home/niubinbin/MyVoldemortMetrics.txt");
//        ExtractionBow extractionBow = new ExtractionBow("MyVoldemort",501,800);
//        List<Map<List<Integer>, StringBuffer>> list = new ArrayList<>();
//        list.add(extractionMeta.getContentMap());
//        list.add(extractionMetrics.getContentMap());
//        list.add(extractionBow.getContentMap());
//        FileOperation.writeContentMap(Merge.mergeMap(list),"/home/niubinbin/MyVoldemortAll.csv");
    }
}