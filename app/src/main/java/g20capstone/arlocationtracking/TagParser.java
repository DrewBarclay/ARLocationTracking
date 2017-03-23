package g20capstone.arlocationtracking;

import android.util.Log;
import android.util.Pair;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Scanner;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by Drew on 2/17/2017.
 * This class originally contained the code for position calculation; it has moved to PositionCalculation.java
 */

public class TagParser {
    private HashMap<Pair<Integer, Integer>, Float> mRanges = new HashMap<>();
    private AtomicReference<HashMap<Pair<Integer, Integer>, Float>> mRangesRef = new AtomicReference<>(null);
    private String data = "";
    public int ourId = 0;

    public void addString(String newData) {
        data += newData;
        String[] lines = data.split("\n");
        for (int i = 0; i < lines.length - 1; i++) {
            parseLine(lines[i]);
        }
        data = lines[lines.length - 1]; //we may have received a partial transmission; deal with this for now

        //Update the shared variable
        mRangesRef.lazySet(new HashMap<>(mRanges));
    }

    private void parseLine(String line) {
        Scanner sc = new Scanner(line);

        try {
            String prefix = sc.next();
            if (prefix.equals("!range")) {
                //This line is intended to convey range information.
                int id1 = sc.nextInt();
                int id2 = sc.nextInt();
                float range = Math.abs(sc.nextFloat());
                if (range < 0.5f) {
                    range = 0.5f;
                }
                //Sanity check it...
                if (range < 1000f) {
                    if (mRanges.containsKey(Pair.create(id1, id2))) {
                        float oldRange = mRanges.get(Pair.create(id1, id2));
                        float newRange = oldRange * 0.7f + range * 0.3f;
                        mRanges.put(Pair.create(id1, id2), newRange);
                        mRanges.put(Pair.create(id2, id1), newRange);
                    } else {
                        mRanges.put(Pair.create(id1, id2), range);
                        mRanges.put(Pair.create(id2, id1), range);
                    }
                    Log.d("TagParser", "" + id1 + " " + id2 + " " + range);
                }
            } else if (prefix.equals("!id")) {
                int id = sc.nextInt();
                ourId = id;
            }
        } catch (Exception e) {
            //Corrupt transmission
            e.printStackTrace();
            return;
        }
    }

    //Thread-safe
    public Map<Pair<Integer, Integer>, Float> getRanges() {
        //Debug ranges:
        if (true) {
            Point3D[] debugPositions = new Point3D[4];
            debugPositions[0] = new Point3D(0, 0, 0); //Our position since by default ourID == 0.
            debugPositions[1] = new Point3D(5, 0, 0);
            debugPositions[2] = new Point3D(-5, 0, 0);
            debugPositions[3] = new Point3D(0, 0, 5);

            HashMap<Pair<Integer, Integer>, Float> debugRanges = new HashMap<>();
            for (int i = 0; i < debugPositions.length; i++) {
                for (int j = 0; j < debugPositions.length; j++) {
                    debugRanges.put(new Pair(i, j), (float)debugPositions[i].distanceTo(debugPositions[j]));
                }
            }
            return debugRanges;
        }

        //If not debugging...
        return mRangesRef.get(); //Get the latest copy of ranges in a thread-safe manner
    }
}