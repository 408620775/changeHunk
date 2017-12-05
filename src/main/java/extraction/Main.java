package extraction;

public class Main {
    public static void main(String[] args) throws Exception {
        ExtractionMeta extractionMeta = new ExtractionMeta("MyVoldemort",501,800);
        extractionMeta.getMetaTableData();
    }
}