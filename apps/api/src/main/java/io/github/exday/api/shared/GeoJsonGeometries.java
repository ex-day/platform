package io.github.exday.api.shared;

import io.github.exday.api.generated.model.Geometry;
import io.github.exday.api.generated.model.LineStringGeometry;
import io.github.exday.api.generated.model.MultiLineStringGeometry;
import io.github.exday.api.generated.model.MultiPolygonGeometry;
import io.github.exday.api.generated.model.PointGeometry;
import io.github.exday.api.generated.model.PolygonGeometry;
import java.util.ArrayList;
import java.util.List;

/**
 * PostGIS の {@code ST_AsGeoJSON} の文字列を、OpenAPI の {@link Geometry}（型で分けた GeoJSON の Geometry）に変える。
 *
 * <p>{@code ST_AsGeoJSON} の出力は {@code {"type":"Point","coordinates":[139.63,35.45]}} の形に決まっているので、
 * JSON の汎用の読み込みは使わず、{@code type} と、数の入れ子の配列（{@code coordinates}）だけを読む。
 */
public final class GeoJsonGeometries {

    private GeoJsonGeometries() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Geometry parse(String geoJson) {
        String type = readType(geoJson);
        int start = geoJson.indexOf("\"coordinates\"");
        if (start < 0) {
            throw new IllegalArgumentException("GeoJSON has no coordinates: " + geoJson);
        }
        int open = geoJson.indexOf('[', start);
        var cursor = new int[] {open};
        List coordinates = (List) readValue(geoJson, cursor);
        return switch (type) {
            case "Point" -> new PointGeometry().type(PointGeometry.TypeEnum.POINT).coordinates(coordinates);
            case "LineString" ->
                new LineStringGeometry().type(LineStringGeometry.TypeEnum.LINE_STRING).coordinates(coordinates);
            case "MultiLineString" -> new MultiLineStringGeometry()
                .type(MultiLineStringGeometry.TypeEnum.MULTI_LINE_STRING).coordinates(coordinates);
            case "Polygon" -> new PolygonGeometry().type(PolygonGeometry.TypeEnum.POLYGON).coordinates(coordinates);
            case "MultiPolygon" ->
                new MultiPolygonGeometry().type(MultiPolygonGeometry.TypeEnum.MULTI_POLYGON).coordinates(coordinates);
            default -> throw new IllegalArgumentException("Unsupported GeoJSON type: " + type);
        };
    }

    private static String readType(String geoJson) {
        int key = geoJson.indexOf("\"type\"");
        int colon = geoJson.indexOf(':', key);
        int open = geoJson.indexOf('"', colon + 1);
        int close = geoJson.indexOf('"', open + 1);
        if (key < 0 || colon < 0 || open < 0 || close < 0) {
            throw new IllegalArgumentException("GeoJSON has no type: " + geoJson);
        }
        return geoJson.substring(open + 1, close);
    }

    /** cursor[0] の位置から、数か、数の入れ子の配列を1つ読む。 */
    private static Object readValue(String text, int[] cursor) {
        skipSpaces(text, cursor);
        if (text.charAt(cursor[0]) == '[') {
            cursor[0]++;
            var list = new ArrayList<Object>();
            skipSpaces(text, cursor);
            if (text.charAt(cursor[0]) == ']') {
                cursor[0]++;
                return list;
            }
            while (true) {
                list.add(readValue(text, cursor));
                skipSpaces(text, cursor);
                char c = text.charAt(cursor[0]++);
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    throw new IllegalArgumentException("Unexpected character in GeoJSON coordinates: " + c);
                }
            }
        }
        int begin = cursor[0];
        while (cursor[0] < text.length() && "+-.0123456789eE".indexOf(text.charAt(cursor[0])) >= 0) {
            cursor[0]++;
        }
        return Double.parseDouble(text.substring(begin, cursor[0]));
    }

    private static void skipSpaces(String text, int[] cursor) {
        while (Character.isWhitespace(text.charAt(cursor[0]))) {
            cursor[0]++;
        }
    }
}
