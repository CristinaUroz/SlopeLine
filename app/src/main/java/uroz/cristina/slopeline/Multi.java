package uroz.cristina.slopeline;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeSet;

class Multi extends Thread {

    private int mPhotoWidth; // Resized bitmap width
    private int mPhotoHeight; // Resized bitmap Heigh
    private int research_area; // Area for research pixels of the same line [1,15]
    private int skipped_pixels; // Skip pixels for searching in the dictionary
    private int[][] mat; // Pixels values
    private Map<String, String> replace_map; // Dictionary
    private Map<Integer, Set<int[]>> lines_fi; // Map with the id of the contour line and all its pixels


    // To search lines in the image
    public void run() {

        int[][] mat_new = new int[mPhotoWidth][mPhotoHeight];

        Map<Integer, Set<int[]>> lines = new HashMap<>();
        Set<int[]> line_pairs = new TreeSet<>(new MyComp());
        Set<int[]> list;
        Set<int[]> list_more;

        for (int y = 0; y < mPhotoHeight; y++) {
            for (int x = 0; x < mPhotoWidth; x++) {
                mat_new[x][y] = -1;
            }
        }

        // Take 5x5 pixel group
        int id = 2;
        for (int Y = 0; Y < mPhotoHeight / skipped_pixels; Y++) {
            int y = Y * skipped_pixels;
            for (int X = 0; X < mPhotoWidth / skipped_pixels; X++) {
                int x = X * skipped_pixels;
                String t = "";
                for (int Y2 = 0; Y2 < 5; Y2++) {
                    int y2 = Y2 + y;
                    for (int X2 = 0; X2 < 5; X2++) {
                        int x2 = X2 + x;
                        if (mat[0].length > y2 & mat.length > x2) {
                            t = t + Integer.toString(mat[x2][y2]);
                        }
                    }
                }
                // Search if it exist in the dictionary
                String t_n = replace_map.get(t);
                if (t_n != null) {
                    boolean trobat = false;
                    list = new TreeSet<>(new MyComp());
                    list_more = new TreeSet<>(new MyComp());
                    char[] tArray = t_n.toCharArray();
                    int id_aux = -2;
                    for (int Y2 = 0; Y2 < 5; Y2++) {
                        int y2 = Y2 + y;
                        for (int X2 = 0; X2 < 5; X2++) {
                            int x2 = X2 + x;
                            int val = Character.getNumericValue(tArray[Y2 * 5 + X2]);
                            if (val == 1) {
                                int i = 2;
                                // Reserch if this 5x5 square belongs to an existing close line
                                while (i < research_area) {
                                    i = i + 2;
                                    for (int Y3 = 0; Y3 < i; Y3++) {
                                        int y3 = Y3 - (i - 2) + y2;
                                        for (int X3 = 0; X3 < i; X3++) {
                                            int x3 = X3 - (i - 2) + x2;
                                            if (x3 >= 0 & y3 >= 0 & x3 < mPhotoWidth & y3 < mPhotoHeight) {
                                                if (mat_new[x3][y3] != 1 & mat_new[x3][y3] != 0 & mat_new[x3][y3] != -1 & !trobat) {
                                                    id_aux = mat_new[x3][y3];
                                                    list_more = lines.get(mat_new[x3][y3]);
                                                    trobat = true;
                                                } else if (mat_new[x3][y3] != id_aux & mat_new[x3][y3] != 1 & mat_new[x3][y3] != 0 & mat_new[x3][y3] != -1 & trobat) {
                                                    int[] temp = new int[2];
                                                    if (id_aux > mat_new[x3][y3]) {
                                                        temp[0] = id_aux;
                                                        temp[1] = mat_new[x3][y3];
                                                    } else {
                                                        temp[1] = id_aux;
                                                        temp[0] = mat_new[x3][y3];
                                                    }
                                                    // Save id pairs of lines that are the same
                                                    line_pairs.add(temp);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (val == 0) {
                                mat_new[x2][y2] = 0;
                            }
                        }
                    }
                    if (!trobat) {
                        id_aux = id;
                    }

                    // Add coordinates to the list
                    for (int Y2 = 0; Y2 < 5; Y2++) {
                        int y2 = Y2 + y;
                        for (int X2 = 0; X2 < 5; X2++) {
                            int x2 = X2 + x;
                            if (Character.getNumericValue(tArray[Y2 * 5 + X2]) == 1) {
                                int[] xy = new int[2];
                                xy[0] = x2;
                                xy[1] = y2;
                                mat_new[x2][y2] = id_aux;
                                list.add(xy);
                            }
                        }
                    }
                    // Add previous line coordinates to the list
                    if (list_more != null) {
                        list.addAll(list_more);
                    }

                    // Add list to the lines map
                    if (list.size() != 0) {
                        lines.put(id_aux, list);
                        if (!trobat) {
                            id++;
                        }
                    }

                }
            }
        }

        // Put together lines that are the same
        lines_fi = new HashMap<>();

        id = 0;
        for (int[] i : line_pairs) {
            list = lines.get(i[1]);
            list_more = lines.get(i[0]);
            if (list != null) {
                if (list.size() > 0) {
                    list_more.addAll(list);
                }
            }

            for (int[] z : line_pairs) {
                if (z[0] == i[0] & z[1] != i[1]) {
                    list = lines.get(z[1]);
                    if (list != null) {
                        if (list.size() > 0) {
                            list_more.addAll(list);
                        }
                    }
                } else if (z[0] == i[1] & z[1] != i[0]) {
                    list = lines.get(z[1]);
                    if (list != null) {
                        if (list.size() > 0) {
                            list_more.addAll(list);
                        }
                    }
                } else if (z[1] == i[0] & z[0] != i[1]) {
                    list = lines.get(z[0]);
                    if (list != null) {
                        if (list.size() > 0) {
                            list_more.addAll(list);
                        }
                    }
                } else if (z[1] == i[1] & z[0] != i[0]) {
                    list = lines.get(z[0]);
                    if (list != null) {
                        if (list.size() > 0) {
                            list_more.addAll(list);
                        }
                    }
                }
            }
            if (list_more != null) {
                if (list_more.size() > 0) {
                    lines_fi.put(id, list_more);
                    id++;
                }
            }
        }

    }

    // Multi Constructor
    public Multi(int mPhotoWidth, int mPhotoHeight, int[][] mat, Map<String, String> replace_map, int research_area, int skipped_pixels) {
        this.mPhotoWidth = mPhotoWidth;
        this.mPhotoHeight = mPhotoHeight;
        this.mat = mat;
        this.replace_map = replace_map;
        this.research_area = research_area;
        this.skipped_pixels = skipped_pixels;
    }

    // To get the final lines map
    public Map<Integer, Set<int[]>> get_lines() {
        return lines_fi;
    }

    class MyComp implements Comparator<int[]> {
        @Override
        public int compare(int[] a, int[] b) {
            if (a[0] < b[0]) {
                return -1;
            } else if (a[0] > b[0]) {
                return 1;
            } else {
                if (a[1] > b[1]) {
                    return 1;
                } else if (a[0] < b[0]) {
                    return -1;
                }
            }
            return 0;
        }

    }

}
