package g20capstone.cameratest;

import android.util.Pair;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Scanner;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Created by Drew on 2/17/2017.
 */

public class TagParser {
    public HashMap<Pair<Integer, Integer>, Float> ranges = new HashMap<>();
    private String data = "";

    public void addString(String newData) {
        data += newData;
        String[] lines = data.split("\n");
        for (int i = 0; i < lines.length - 1; i++) {
            parseLine(lines[i]);
        }
        data = lines[lines.length - 1]; //we may have received a partial transmission; deal with this for now
    }

    private void parseLine(String line) {
        Scanner sc = new Scanner(line);

        try {
            String prefix = sc.next();
            if (prefix.equals("!range")) {
                //This line is intended to convey range information.
                int id1 = sc.nextInt();
                int id2 = sc.nextInt();
                float range = sc.nextFloat();
                ranges.put(Pair.create(id1, id2), range);
            }
        } catch (Exception e) {
            return;
        }
    }

    public HashMap<Integer, Point3D> getPositions() {
        //Start by making the adjacency matrix

        //Begin by turning ids into integers from 0-size
        SortedSet<Integer> ss = new TreeSet<>();
        for (Map.Entry<Pair<Integer, Integer>, Float> entry : ranges.entrySet()) {
            Pair<Integer, Integer> p = entry.getKey();
            ss.add(p.first);
            ss.add(p.second);
        }

        float[][] distances = new float[ss.size()][ss.size()];
        if (distances.length < 4) {
            return null; //not enough points
        }

        HashMap<Integer, Integer> idToIndex = new HashMap<>();
        HashMap<Integer, Integer> indexToId = new HashMap<>();
        Iterator<Integer> it = ss.iterator();
        int curIndex = 0;
        while (it.hasNext()) {
            int id = it.next();
            idToIndex.put(id, curIndex);
            indexToId.put(curIndex, id);
            curIndex++;
        }

        //Now we make the adjacency matrix properly!
        for (Map.Entry<Pair<Integer, Integer>, Float> entry : ranges.entrySet()) {
            Pair<Integer, Integer> p = entry.getKey();
            float range = entry.getValue();
            distances[idToIndex.get(p.first)][idToIndex.get(p.second)] = range;
        }

        Point3D[] positions = new Point3D[distances.length];
        for (int i = 0; i < positions.length; i++) {
            positions[i] = new Point3D(0, 0, 0);
        }

        //Arbitrarily declare positions of first two...
        positions[0] = new Point3D(0, 0, 0);
        positions[1] = new Point3D(distances[0][1], 0, 0);

        //We're now going to make a triangle. First two anchors from a horizontal line, anchor 3 will form a triangle. (We assume +y.)
        //Anchor 1 is at (0, 0, 0) so if we get the angle there we can use sin/cos * length of distance from anchor 1 to anchor 3 to calc its position.
        //Use cosine law and to determine third point, arbitrarily with z=0...
        //Find the angle at the top-left point (which will be C, opposite will be distance from 2nd anchor to 3rd anchor.)
        //c^2 = a^2 + b^2 - 2*a*b*cos(C)
        //C (the angle opposite the side) = acos(a^2 + b^2 - c^2 over 2*a*b)
        positions[2] = calculate2DDistance(distances[0][1], distances[0][2], distances[1][2]);

        //Now we calculate anchor 4. We will get one of two possible answers: one with a +z, another with a -z.
        //Arbitrarily choose +z. Cellphone will figure out orientation later.
        //To figure out things, we use the cosine law as previous, but then, instead of assuming z=0, we use the assumed z=0 as a starting point (which we know is wrong) and rotate around the x axis until the range from fourth anchor to third anchor is correct. (Thus how we could get two answers: we can rotate in one of two directions until it's right.)
        Point3D temp = calculate2DDistance(distances[0][1], distances[0][3], distances[1][3]);
        //Now we want to rotate until the distance from anchor 3 is correct.
        //Rotating around the x axis is accomplished by holding x constant and taking the y distance and z distance to be a circle of radius (2D y, aka temp.y).
        //So, the math to get things correct is:
        //distance from index 2 to index 3 (anchors 3 to 4) == distance between pos2 and (temp.x, cos(angle) * temp.y, sin(angle) * temp.y)
        //distance[2][3] == sqrt((pos2.x - temp.x)^2 + (pos2.y - cos(angle)*temp.y)^2 + (0 - sin(angle)*temp.y)^2)
        //distance[2][3]^2 == (pos2.x - temp.x)^2 + pos2.y^2 - 2*pos.y*cos(angle)*temp.y + (cos(angle)*temp.y)^2 + (sin(angle)*temp.y)^2
        //Use sin^2 = 1-cos^2...
        //To shorten things, delta_x = temp.x - pos2.x, distance -> d
        //d[2][3]^2 == delta_x^2 + pos2.y^2 - 2*pos2.y*cos(angle)*temp.y + (cos(angle)*temp.y)^2 + temp.y^2 - cos^2(angle)*temp.y^2
        //Simplify this ugly thing...
        //(temp.y^2-temp.y^2)*cos^2(angle) - 2*pos2.y*temp.y*cos(angle) + (delta_x^2 - d[2][3]^2 + pos2.y^2 + temp.y^2) = 0
        //I'll admit I thought it'd be a quadratic but it turns out the first part cancels, leaving us with...
        //2*pos2.y*temp.y*cos(angle) = delta_x^2 - d[2][3]^2 + pos2.y^2 + temp.y^2
        //cos(angle) = (delta_x^2 - d[2][3]^2 + pos2.y^2 + temp.y^2) / (2*pos2.y*temp.y)
        //angle = acos((delta_x^2 - d[2][3]^2 + pos2.y^2 + temp.y^2) / (2*pos2.y*temp.y))
        double deltaX = (temp.x - positions[2].x);
        double angle = Math.acos((deltaX*deltaX - distances[2][3]*distances[2][3] + positions[2].y*positions[2].y + temp.y*temp.y) / (2 * positions[2].y * temp.y));
        positions[3] = new Point3D(temp.x, temp.y * Math.cos(angle), temp.y * Math.sin(angle));

        //Now for the tricky part. Before, any of the distances between anchors could be off/noisy and we had an exact mathematical solution.
        //This is not the case now that we've set the anchors' positions. With any noise at all, we will run into events where reported ranges can not be placed exactly in space.
        //Our goal is to minimize the error of any expected position.
        //What does this mean? It means if we pick an (x, y, z) point, and use the reported distance to anchors to draw a vector from the anchors in the exact direction of this point, we want to minimize how much overshoot/undershoot there is, eg. maybe anchor 1 is too short by 0.9 meters to touch our estimated location and the rest of the anchors are exactly on, but if we can find a better place which makes makes the error 0.1m to every anchor that would be better.
        //The solution below does ***not*** satisfy this goal. It is is simply a copy of the above code for calculating the fourth anchor, essentially. One possible way to improve on this would be to perform the calculation with the order of the distances from the anchors swapped around. Given there's four anchors, doing every combo would give us 4! = 24 possibilities. We could then loop over every possibility and find the one with the minimum error, or we could average them all together. This is a bit slow, so we're not doing it now, but as part of refining the design later it would be a good idea to look into this or more complex mathematical solutions. For now, it will work alright.
        //The below code will be correct for 'exact' distances, but when noise/error are introduced it will not be as good as it could be. This should be revisited in the future.
        for (int i = 4; i < distances.length; i++) {
            //For each tag (index i)...
            temp = calculate2DDistance(distances[0][1], distances[0][i], distances[1][i]);
            deltaX = (temp.x - positions[2].x);
            angle = Math.acos((deltaX*deltaX - distances[2][i]*distances[2][i] + positions[2].y*positions[2].y + temp.y*temp.y) / (2 * positions[2].y * temp.y));
            //Because cos(x) = cos(-x), we need to check now if we want -angle or +angle (before we arbitrarily chose +angle but we can't do that any longer)
            //We can do this by making the point for each angle and choosing whichever has less of an error to anchor 4 (index 3)
            Point3D posAnglePoint = new Point3D(temp.x, temp.y * Math.cos(angle), temp.y * Math.sin(angle));
            Point3D negAnglePoint = new Point3D(temp.x, temp.y * Math.cos(-angle), temp.y * Math.sin(-angle));
            double posAngleError = Math.abs(posAnglePoint.distanceTo(positions[3]) - distances[3][i]);
            double negAngleError = Math.abs(negAnglePoint.distanceTo(positions[3]) - distances[3][i]);
            //Now we measure...
            if (posAngleError < negAngleError) {
                positions[i] = posAnglePoint;
            } else {
                positions[i] = negAnglePoint;
            }
        }

        HashMap<Integer, Point3D> idPositions = new HashMap<>();
        for (int i = 0; i < positions.length; i++) {
            idPositions.put(indexToId.get(i), positions[i]);
        }

        return idPositions;
    }

    //c is the side opposite the 'origin' point,
    //b is the length from origin to point we're calculating the 2D position of
    private static Point3D calculate2DDistance(double a, double b, double c) {
        double C = Math.acos((a*a + b*b - c*c) / (2 * a * b));
        return new Point3D(Math.cos(C) * b, Math.sin(C) * b, 0);
    }
}
