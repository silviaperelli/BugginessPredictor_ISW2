package utils;

import java.util.Collections;
import java.util.List;

public class MathUtils {

    public static float median(List<Float> array){
        float median;

        Collections.sort(array);

        int size = array.size();
        if (size % 2 == 0) {
            median = (array.get((size / 2) - 1) + array.get(size / 2)) / 2;
        } else {
            median = array.get(size / 2);
        }
        return median;
    }
}
