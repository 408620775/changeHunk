package extraction;

public class Main {
    public static void main(String[] args) throws Exception {
        ExtractionMeta extractionMeta = new ExtractionMeta("MyVoldemort",501,800);
        //extractionMeta.getMetaTableData();
        //System.out.println(extractionMeta.countRatio());
        extractionMeta.just_in_time("/home/niubinbin/test/voldemort");
    }
}