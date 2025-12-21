import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * ADOFAI Timing Calculator Library
 * A Dance of Fire and Ice 레벨 파일의 노트 타이밍을 계산하는 라이브러리
 * 
 * 기능:
 * - .adofai 레벨 파일 파싱
 * - angleData / pathData → 각도 변환
 * - BPM, SetSpeed, Twirl, Pause, Hold, MultiPlanet 이벤트 처리
 * - 노트별 정확한 타이밍(ms) 계산
 * 
 * 사용 예시:
 * ```java
 * ADOFAITimingLib lib = new ADOFAITimingLib();
 * lib.loadLevel("level.adofai");
 * List<Double> timings = lib.calculateNoteTimes();
 * LevelInfo info = lib.getLevelInfo();
 * ```
 * 
 * @license MIT
 */
public class ADOFAITimingLib {
    
    // 레벨 데이터
    private Map<String, Object> levelData;
    private List<Double> angleData;
    private List<Map<String, Object>> events;
    private double baseBPM;
    private int offset;
    private double pitch;
    private int countdownTicks;
    
    // 각도 문자 매핑
    private static final Map<Character, double[]> ANGLE_MAP = new HashMap<>();
    
    static {
        // [각도, 상대적 여부(0=절대, 1=상대)]
        // 절대 각도
        ANGLE_MAP.put('R', new double[]{0, 0});
        ANGLE_MAP.put('p', new double[]{15, 0});
        ANGLE_MAP.put('J', new double[]{30, 0});
        ANGLE_MAP.put('E', new double[]{45, 0});
        ANGLE_MAP.put('T', new double[]{60, 0});
        ANGLE_MAP.put('o', new double[]{75, 0});
        ANGLE_MAP.put('U', new double[]{90, 0});
        ANGLE_MAP.put('q', new double[]{105, 0});
        ANGLE_MAP.put('G', new double[]{120, 0});
        ANGLE_MAP.put('Q', new double[]{135, 0});
        ANGLE_MAP.put('H', new double[]{150, 0});
        ANGLE_MAP.put('W', new double[]{165, 0});
        ANGLE_MAP.put('L', new double[]{180, 0});
        ANGLE_MAP.put('x', new double[]{195, 0});
        ANGLE_MAP.put('N', new double[]{210, 0});
        ANGLE_MAP.put('Z', new double[]{225, 0});
        ANGLE_MAP.put('F', new double[]{240, 0});
        ANGLE_MAP.put('V', new double[]{255, 0});
        ANGLE_MAP.put('D', new double[]{270, 0});
        ANGLE_MAP.put('Y', new double[]{285, 0});
        ANGLE_MAP.put('B', new double[]{300, 0});
        ANGLE_MAP.put('C', new double[]{315, 0});
        ANGLE_MAP.put('M', new double[]{330, 0});
        ANGLE_MAP.put('A', new double[]{345, 0});
        // 상대 각도
        ANGLE_MAP.put('5', new double[]{108, 1});
        ANGLE_MAP.put('6', new double[]{252, 1});
        ANGLE_MAP.put('7', new double[]{900.0 / 7.0, 1});
        ANGLE_MAP.put('8', new double[]{360 - 900.0 / 7.0, 1});
        ANGLE_MAP.put('t', new double[]{60, 1});
        ANGLE_MAP.put('h', new double[]{120, 1});
        ANGLE_MAP.put('j', new double[]{240, 1});
        ANGLE_MAP.put('y', new double[]{300, 1});
        ANGLE_MAP.put('!', new double[]{999, 1}); // 중간 회전(Midspin)
    }
    
    /**
     * 레벨 정보를 담는 클래스
     */
    public static class LevelInfo {
        public String song;
        public String artist;
        public String author;
        public double bpm;
        public int offset;
        public double pitch;
        public int countdownTicks;
        public int totalTiles;
        public double totalDuration; // ms
        
        @Override
        public String toString() {
            return String.format(
                "=== Level Info ===\n" +
                "Song: %s\n" +
                "Artist: %s\n" +
                "Author: %s\n" +
                "BPM: %.2f\n" +
                "Offset: %d ms\n" +
                "Pitch: %.0f%%\n" +
                "Countdown: %d ticks\n" +
                "Total Tiles: %d\n" +
                "Duration: %.2f seconds\n",
                song, artist, author, bpm, offset, pitch, 
                countdownTicks, totalTiles, totalDuration / 1000.0
            );
        }
    }
    
    /**
     * 기본 생성자
     */
    public ADOFAITimingLib() {
        this.pitch = 100.0;
    }
    
    /**
     * .adofai 레벨 파일을 로드합니다.
     * 
     * @param filePath 레벨 파일 경로
     * @throws IOException 파일 읽기 실패 시
     */
    public void loadLevel(String filePath) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(filePath)), "UTF-8");
        
        // BOM 제거
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }
        
        levelData = parseSimpleJSON(content);
        
        // 설정 로드
        Map<String, Object> settings = (Map<String, Object>) levelData.get("settings");
        if (settings != null) {
            baseBPM = getDouble(settings, "bpm", 100);
            offset = getInt(settings, "offset", 0);
            pitch = getDouble(settings, "pitch", 100);
            countdownTicks = getInt(settings, "countdownTicks", 0);
        } else {
            baseBPM = 100;
            offset = 0;
            pitch = 100;
            countdownTicks = 0;
        }
        
        // 각도 데이터 로드
        Object angleDataObj = levelData.get("angleData");
        if (angleDataObj != null && angleDataObj instanceof List) {
            // angleData 방식 (새 포맷)
            List<?> angles = (List<?>) angleDataObj;
            angleData = new ArrayList<>();
            for (Object angle : angles) {
                if (angle instanceof Number) {
                    angleData.add(((Number) angle).doubleValue());
                }
            }
        } else {
            // pathData 방식 (구 포맷)
            String pathData = getString((Map<String, Object>) levelData, "pathData", "");
            angleData = pathDataToAngles(pathData);
        }
        
        // 이벤트 로드
        events = new ArrayList<>();
        Object actionsObj = levelData.get("actions");
        if (actionsObj != null && actionsObj instanceof List) {
            for (Object action : (List<?>) actionsObj) {
                if (action instanceof Map) {
                    events.add((Map<String, Object>) action);
                }
            }
        }
    }
    
    /**
     * 레벨 정보를 반환합니다.
     * 
     * @return LevelInfo 객체
     */
    public LevelInfo getLevelInfo() {
        LevelInfo info = new LevelInfo();
        
        Map<String, Object> settings = (Map<String, Object>) levelData.get("settings");
        if (settings != null) {
            info.song = getString(settings, "song", "Unknown");
            info.artist = getString(settings, "artist", "Unknown");
            info.author = getString(settings, "author", "Unknown");
        } else {
            info.song = "Unknown";
            info.artist = "Unknown";
            info.author = "Unknown";
        }
        
        info.bpm = baseBPM;
        info.offset = offset;
        info.pitch = pitch;
        info.countdownTicks = countdownTicks;
        info.totalTiles = angleData != null ? angleData.size() : 0;
        
        // 총 시간 계산
        List<Double> times = calculateNoteTimes();
        info.totalDuration = times.isEmpty() ? 0 : times.get(times.size() - 1);
        
        return info;
    }
    
    /**
     * 모든 노트의 타이밍을 계산합니다.
     * 
     * @return 각 노트의 타이밍(ms) 리스트
     */
    public List<Double> calculateNoteTimes() {
        if (angleData == null || angleData.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<Double> noteTimes = new ArrayList<>();
        
        // 중간 회전(Midspin) 처리를 위한 인덱스 추적
        List<Integer> midspinIndices = new ArrayList<>();
        
        // 각 타일의 정보 저장
        List<Map<String, Object>> tileData = new ArrayList<>();
        
        for (int i = 0; i < angleData.size(); i++) {
            double angle = angleData.get(i);
            if (angle == 999) {
                midspinIndices.add(i - 1);
                if (tileData.size() > 0) {
                    tileData.get(tileData.size() - 1).put("midspin", true);
                }
            } else {
                Map<String, Object> tile = new HashMap<>();
                tile.put("angle", fmod(angle, 360));
                tile.put("bpm", -1.0);
                tile.put("direction", 1);
                tile.put("extraHold", 0.0);
                tile.put("midspin", false);
                tile.put("multiPlanet", false);
                tileData.add(tile);
            }
        }
        
        // 이벤트 처리
        double currentBPM = baseBPM * (pitch / 100.0);
        
        for (Map<String, Object> event : events) {
            int floor = getInt(event, "floor", 0);
            String eventType = getString(event, "eventType", "");
            
            int adjustedFloor = floor - upperBound(midspinIndices, floor);
            if (adjustedFloor < 0 || adjustedFloor >= tileData.size()) continue;
            
            Map<String, Object> tile = tileData.get(adjustedFloor);
            
            switch (eventType) {
                case "SetSpeed":
                    String speedType = getString(event, "speedType", "Bpm");
                    if ("Multiplier".equals(speedType)) {
                        double multiplier = getDouble(event, "bpmMultiplier", 1.0);
                        tile.put("bpm", currentBPM * multiplier);
                        currentBPM *= multiplier;
                    } else {
                        double newBPM = getDouble(event, "beatsPerMinute", currentBPM);
                        tile.put("bpm", newBPM * (pitch / 100.0));
                        currentBPM = newBPM * (pitch / 100.0);
                    }
                    break;
                case "Twirl":
                    tile.put("direction", -1);
                    break;
                case "Pause":
                    double pauseDuration = getDouble(event, "duration", 0);
                    tile.put("extraHold", (double) tile.get("extraHold") + pauseDuration / 2.0);
                    break;
                case "Hold":
                    double holdDuration = getDouble(event, "duration", 0);
                    tile.put("extraHold", (double) tile.get("extraHold") + holdDuration);
                    break;
                case "MultiPlanet":
                    String planets = getString(event, "planets", "TwoPlanets");
                    tile.put("multiPlanet", "ThreePlanets".equals(planets));
                    break;
            }
        }
        
        // BPM 및 방향 전파
        double BPM = baseBPM * (pitch / 100.0);
        int direction = 1;
        
        for (Map<String, Object> tile : tileData) {
            if ((int) tile.get("direction") == -1) {
                direction = -direction;
            }
            tile.put("direction", direction);
            
            double tileBPM = (double) tile.get("bpm");
            if (tileBPM < 0) {
                tile.put("bpm", BPM);
            } else {
                BPM = tileBPM;
            }
        }
        
        // 타이밍 계산
        double curAngle = 0;
        double curTime = 0;
        boolean isMultiPlanet = false;
        
        for (int i = 0; i < tileData.size(); i++) {
            Map<String, Object> tile = tileData.get(i);
            
            curAngle = fmod(curAngle - 180, 360);
            double tileBPM = (double) tile.get("bpm");
            double destAngle = (double) tile.get("angle");
            int tileDirection = (int) tile.get("direction");
            
            double pAngle;
            if (Math.abs(destAngle - curAngle) <= 0.001) {
                pAngle = 360;
            } else {
                pAngle = fmod((curAngle - destAngle) * tileDirection, 360);
            }
            
            pAngle += (double) tile.get("extraHold") * 360;
            
            // MultiPlanet 처리
            if (isMultiPlanet) {
                if (pAngle > 60) pAngle -= 60;
                else pAngle += 300;
            }
            
            if ((boolean) tile.get("multiPlanet")) {
                isMultiPlanet = true;
                if (pAngle > 60) pAngle -= 60;
                else pAngle += 300;
            }
            
            curTime += angleToTime(pAngle, tileBPM);
            curAngle = destAngle;
            
            if ((boolean) tile.get("midspin")) {
                curAngle = curAngle + 180;
            }
            
            noteTimes.add(curTime);
        }
        
        return noteTimes;
    }
    
    /**
     * 자동 오프셋을 계산합니다.
     * countdownTicks와 레벨 offset을 기반으로 계산
     * 
     * @return 자동 오프셋 (ms)
     */
    public double calculateAutoOffset() {
        double effectiveBPM = baseBPM * (pitch / 100.0);
        double beatDuration = 60000.0 / effectiveBPM;
        double countdownTime = countdownTicks * beatDuration;
        return offset + countdownTime;
    }
    
    /**
     * pathData 문자열을 각도 리스트로 변환합니다.
     */
    private List<Double> pathDataToAngles(String pathData) {
        List<Double> angles = new ArrayList<>();
        double staticAngle = 0;
        
        for (char c : pathData.toCharArray()) {
            double[] angleInfo = ANGLE_MAP.get(c);
            if (angleInfo == null) continue;
            
            double angle = angleInfo[0];
            boolean relative = angleInfo[1] == 1;
            
            if (angle == 999) {
                angles.add(999.0);
                continue;
            }
            
            if (relative) {
                staticAngle = generalizeAngle(staticAngle + 180 - angle);
            } else {
                staticAngle = angle;
            }
            angles.add(staticAngle);
        }
        
        return angles;
    }
    
    private double generalizeAngle(double angle) {
        angle = angle - ((int) (angle / 360)) * 360;
        return angle < 0 ? angle + 360 : angle;
    }
    
    private double fmod(double a, double b) {
        double t = Math.floor(a / b);
        return a - b * t;
    }
    
    private double angleToTime(double angle, double bpm) {
        return (angle / 180.0) * (60.0 / bpm) * 1000.0;
    }
    
    private int upperBound(List<Integer> list, int value) {
        int left = 0, right = list.size();
        while (left < right) {
            int mid = (left + right) / 2;
            if (list.get(mid) <= value) {
                left = mid + 1;
            } else {
                right = mid;
            }
        }
        return left;
    }
    
    // ===== JSON 파싱 유틸리티 =====
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSimpleJSON(String json) {
        return (Map<String, Object>) parseValue(new JSONParser(json));
    }
    
    private Object parseValue(JSONParser parser) {
        parser.skipWhitespace();
        if (!parser.hasMore()) return null;
        char c = parser.peek();
        
        if (c == '\0') return null;
        if (c == '{') return parseObject(parser);
        if (c == '[') return parseArray(parser);
        if (c == '"') return parseString(parser);
        if (c == 't' || c == 'f') return parseBoolean(parser);
        if (c == 'n') return parseNull(parser);
        if (Character.isDigit(c) || c == '-' || c == '.') return parseNumber(parser);
        
        parser.next();
        return parseValue(parser);
    }
    
    private Map<String, Object> parseObject(JSONParser parser) {
        Map<String, Object> map = new LinkedHashMap<>();
        parser.consume('{');
        parser.skipWhitespace();
        
        if (parser.peek() == '}') {
            parser.consume('}');
            return map;
        }
        
        while (true) {
            parser.skipWhitespace();
            
            if (parser.peek() == '}') {
                parser.consume('}');
                break;
            }
            
            if (parser.peek() != '"') {
                parser.skipUntil("},");
                if (parser.hasMore() && parser.peek() == '}') {
                    parser.consume('}');
                }
                break;
            }
            
            String key = parseString(parser);
            parser.skipWhitespace();
            parser.consume(':');
            Object value = parseValue(parser);
            map.put(key, value);
            
            parser.skipWhitespace();
            if (parser.peek() == '}') {
                parser.consume('}');
                break;
            }
            if (parser.peek() == ',') {
                parser.consume(',');
                parser.skipWhitespace();
            }
        }
        
        return map;
    }
    
    private List<Object> parseArray(JSONParser parser) {
        List<Object> list = new ArrayList<>();
        parser.consume('[');
        parser.skipWhitespace();
        
        if (parser.peek() == ']') {
            parser.consume(']');
            return list;
        }
        
        while (true) {
            parser.skipWhitespace();
            
            if (parser.peek() == ']') {
                parser.consume(']');
                break;
            }
            
            list.add(parseValue(parser));
            parser.skipWhitespace();
            if (parser.peek() == ']') {
                parser.consume(']');
                break;
            }
            if (parser.peek() == ',') {
                parser.consume(',');
                parser.skipWhitespace();
            }
        }
        
        return list;
    }
    
    private String parseString(JSONParser parser) {
        parser.consume('"');
        StringBuilder sb = new StringBuilder();
        
        while (parser.hasMore() && parser.peek() != '"' && parser.peek() != '\0') {
            char c = parser.next();
            if (c == '\\') {
                if (!parser.hasMore()) break;
                char escaped = parser.next();
                switch (escaped) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        try {
                            String hex = "" + parser.next() + parser.next() + parser.next() + parser.next();
                            sb.append((char) Integer.parseInt(hex, 16));
                        } catch (Exception e) {
                            sb.append("?");
                        }
                        break;
                    default: sb.append(escaped);
                }
            } else {
                sb.append(c);
            }
        }
        if (parser.hasMore() && parser.peek() == '"') {
            parser.consume('"');
        }
        return sb.toString();
    }
    
    private Number parseNumber(JSONParser parser) {
        StringBuilder sb = new StringBuilder();
        
        if (parser.peek() == '-') sb.append(parser.next());
        
        while (parser.hasMore() && (Character.isDigit(parser.peek()) || parser.peek() == '.' || 
               parser.peek() == 'e' || parser.peek() == 'E' || parser.peek() == '+' || parser.peek() == '-')) {
            sb.append(parser.next());
        }
        
        String num = sb.toString();
        if (num.contains(".") || num.contains("e") || num.contains("E")) {
            return Double.parseDouble(num);
        }
        return Long.parseLong(num);
    }
    
    private Boolean parseBoolean(JSONParser parser) {
        if (parser.peek() == 't') {
            parser.consume('t'); parser.consume('r'); parser.consume('u'); parser.consume('e');
            return true;
        } else {
            parser.consume('f'); parser.consume('a'); parser.consume('l'); parser.consume('s'); parser.consume('e');
            return false;
        }
    }
    
    private Object parseNull(JSONParser parser) {
        parser.consume('n'); parser.consume('u'); parser.consume('l'); parser.consume('l');
        return null;
    }
    
    private static class JSONParser {
        String json;
        int pos;
        
        JSONParser(String json) {
            this.json = json;
            this.pos = 0;
        }
        
        boolean hasMore() { return pos < json.length(); }
        char peek() { return pos >= json.length() ? '\0' : json.charAt(pos); }
        char next() { return pos >= json.length() ? '\0' : json.charAt(pos++); }
        
        void consume(char expected) {
            skipWhitespace();
            if (!hasMore()) return;
            if (peek() == expected) pos++;
        }
        
        void skipWhitespace() {
            while (hasMore() && Character.isWhitespace(peek())) pos++;
        }
        
        void skipUntil(String chars) {
            while (hasMore() && chars.indexOf(peek()) == -1) pos++;
        }
    }
    
    // Helper 메서드들
    private double getDouble(Map<String, Object> map, String key, double defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).doubleValue();
        return Double.parseDouble(value.toString());
    }
    
    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        return Integer.parseInt(value.toString());
    }
    
    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        return value.toString();
    }
    
    // ===== 메인 (예제 실행) =====
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("ADOFAI Timing Calculator Library");
            System.out.println("================================");
            System.out.println();
            System.out.println("Usage: java ADOFAITimingLib <level.adofai>");
            System.out.println();
            System.out.println("Example:");
            System.out.println("  java ADOFAITimingLib \"My Level.adofai\"");
            System.out.println();
            System.out.println("Output:");
            System.out.println("  - Level information (BPM, offset, etc.)");
            System.out.println("  - Note timings in milliseconds");
            return;
        }
        
        try {
            ADOFAITimingLib lib = new ADOFAITimingLib();
            lib.loadLevel(args[0]);
            
            // 레벨 정보 출력
            LevelInfo info = lib.getLevelInfo();
            System.out.println(info);
            
            // 노트 타이밍 출력
            List<Double> timings = lib.calculateNoteTimes();
            System.out.println("=== Note Timings (first 20) ===");
            for (int i = 0; i < Math.min(20, timings.size()); i++) {
                System.out.printf("Tile %3d: %8.2f ms%n", i + 1, timings.get(i));
            }
            
            if (timings.size() > 20) {
                System.out.println("... (" + (timings.size() - 20) + " more tiles)");
            }
            
            // 자동 오프셋 정보
            System.out.println();
            System.out.printf("Auto Offset: %.2f ms%n", lib.calculateAutoOffset());
            
        } catch (IOException e) {
            System.err.println("Error loading level: " + e.getMessage());
        }
    }
}
