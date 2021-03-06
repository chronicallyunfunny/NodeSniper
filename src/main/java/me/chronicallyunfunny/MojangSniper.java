package me.chronicallyunfunny;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class MojangSniper implements Sniper {
    private final HttpClient client = HttpClient.newHttpClient();
    private String username = null;
    private String password = null;
    private String sq1 = null;
    private String sq2 = null;
    private String sq3 = null;
    private String authToken = null;
    private String snipedUsername = null;
    private final String[] questionIDArray = new String[3];
    private int delay;
    private Instant dropTime;
    private HttpRequest snipeRequest;
    private boolean turboSnipe = false;
    private final AtomicBoolean isSuccessful = new AtomicBoolean(false);
    private final int NO_OF_REQUESTS = 2;
    private final List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private int spread;
    private String skinVariant;
    private boolean isChangeSkin;
    private String skinPath;

    @Override
    public void printSplashScreen() {
        System.out.println("    _   __          __    _____       _                ");
        System.out.println("   / | / /___  ____/ /__ / ___/____  (_)___  ___  _____");
        System.out.println("  /  |/ / __ \\/ __  / _ \\\\__ \\/ __ \\/ / __ \\/ _ \\/ ___/");
        System.out.println(" / /|  / /_/ / /_/ /  __/__/ / / / / / /_/ /  __/ /    ");
        System.out.println("/_/ |_/\\____/\\__,_/\\___/____/_/ /_/_/ .___/\\___/_/     ");
        System.out.println("                                   /_/                 ");
        System.out.println();
        System.out.println("Developed by @chronicallyunfunny#1113 on Discord.");
        System.out.println();
        System.out.println("Initialising...");
        System.out.println();
    }

    @Override
    public void getUsernameChoice() {
        var scanner = new Scanner(System.in);
        System.out.print("What name will you like to snipe: ");
        snipedUsername = scanner.next().replaceAll("\\s+", "");
        scanner.close();
        if ((snipedUsername.length() < 3) || (snipedUsername.length() > 16)
                || (!(snipedUsername.matches("[A-Za-z0-9_]+"))))
            throw new GeneralSniperException("[GetUsernameChoice] You entered an invalid username.");
    }

    @Override
    public void parseAccountFile() throws IOException {
        var fileName = Path.of("account.yml");
        var actual = Files.readString(fileName);
        var yaml = new Yaml();
        Map<String, String> accountData = yaml.load(actual);
        username = accountData.get("username");
        // a hack to protect against noob users whose passwords are made up of all
        // numbers
        password = String.valueOf(accountData.get("password"));
        sq1 = accountData.get("sq1");
        sq2 = accountData.get("sq2");
        sq3 = accountData.get("sq3");
        if ((username == null) || (password == null))
            throw new GeneralSniperException(
                    "[ParseAccountFile] The username or password field in account.yml is empty.");
    }

    @Override
    public void parseConfigFile() throws IOException {
        var fileName = Path.of("config.yml");
        var actual = Files.readString(fileName);
        var yaml = new Yaml();
        Map<String, Object> accountData = yaml.load(actual);
        delay = (int) accountData.get("delay");
        spread = (int) accountData.get("spread");
        skinVariant = (String) accountData.get("skinModel");
        skinVariant = skinVariant.toLowerCase();
        isChangeSkin = (boolean) accountData.get("changeSkin");
        if (isChangeSkin)
            if (!((skinVariant.equals("slim")) || (skinVariant.equals("classic"))))
                throw new GeneralSniperException("[ConfigParser] Invalid skin type.");
        skinPath = (String) accountData.get("skinFileName");
        System.out.println("Delay is set to " + delay + " ms.");
    }

    @Override
    public void isNameAvailable() throws URISyntaxException, IOException, InterruptedException {
        var uri = new URI("https://api.mojang.com/user/profile/agent/minecraft/name/" + snipedUsername);
        var request = HttpRequest.newBuilder().uri(uri).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.discarding());
        switch (response.statusCode()) {
        case 204:
            return;
        case 200:
            throw new GeneralSniperException("[NameAvailabilityChecker] Name has been taken.");
        default:
            throw new GeneralSniperException("[NameAvailabilityChecker] HTTP status code: " + response.statusCode());
        }
    }

    @Override
    public void execute() throws URISyntaxException {
        var timer2 = new Timer();
        TimerTask snipe = new TimerTask() {
            @Override
            public void run() {
                try {
                    for (var request = 1; request <= NO_OF_REQUESTS; request++) {
                        var snipe = client.sendAsync(snipeRequest, HttpResponse.BodyHandlers.discarding())
                                .thenApply(HttpResponse::statusCode).thenAccept(code -> {
                                    var now = Instant.now();
                                    var accurateDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                                            .withZone(ZoneId.systemDefault());
                                    var accurateTime = accurateDateFormat.format(now);
                                    var keyword = "fail";
                                    if (code == 200) {
                                        isSuccessful.set(true);
                                        keyword = "success";
                                    }
                                    System.out.println("[" + keyword + "] " + code + " @ " + accurateTime);
                                });
                        completableFutures.add(snipe);
                        Thread.sleep(spread);
                    }
                    CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0])).join();
                    if (isSuccessful.get()) {
                        System.out.println("You have successfully sniped the name " + snipedUsername + "!");
                        if (isChangeSkin) {
                            var file = Files.readAllBytes(Path.of(skinPath));
                            var strFile = Base64.getEncoder().encodeToString(file);
                            var postJSON = "{\"file\":\"" + strFile + "\",\"variant\":\"" + skinVariant + "\"}";
                            var uri = new URI("https://api.minecraftservices.com/minecraft/profile/skins");
                            var request = HttpRequest.newBuilder().uri(uri)
                                    .headers("Authorization", "Bearer " + authToken, "Content-Type",
                                            "multipart/form-data")
                                    .POST(HttpRequest.BodyPublishers.ofString(postJSON)).build();
                            var response = client.send(request, HttpResponse.BodyHandlers.discarding());
                            if (!(response.statusCode() == 200))
                                throw new GeneralSniperException(
                                        "[SkinChanger] HTTP status code: " + response.statusCode());
                            System.out.println("Successfully changed skin!");
                        }
                    }
                    System.out.print("Press ENTER to quit: ");
                    System.in.read();
                    timer2.cancel();
                    System.exit(0);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        };
        // I've given up on making the code clean.
        if (turboSnipe) {
            System.out.println(
                    "Warning: Some usernames may show up as available but has been blocked by Mojang. Sniping it will not work.");
            System.out.println("Signed in to " + username + ".");
            var uri = new URI("https://api.minecraftservices.com/minecraft/profile/name/" + snipedUsername);
            snipeRequest = HttpRequest.newBuilder().uri(uri).header("Authorization", "Bearer " + authToken)
                    .PUT(HttpRequest.BodyPublishers.noBody()).build();
            System.out.println("Setup complete!");
            snipe.run();
        }
        var now = Instant.now();
        var semiAccurateDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault());
        var niceDropTime = semiAccurateDateFormat.format(dropTime);
        var diffInTime = (dropTime.getEpochSecond() - now.getEpochSecond()) / 60;
        System.out.println(
                "Sniping " + snipedUsername + " in ~" + diffInTime + " minutes | sniping at " + niceDropTime + ".");
        var authenticationTime = Date.from(dropTime.minusSeconds(60));
        var delayAdjustedTime = Date.from(dropTime.minusMillis(delay));
        var timer1 = new Timer();
        final TimerTask authentication = new TimerTask() {
            @Override
            public void run() {
                try {
                    authenticate();
                    if (isSecurityQuestionsNeeded())
                        if (getSecurityQuestionsID())
                            sendSecurityQuestions();
                    isNameChangeEligible();
                    isNameAvailable();
                    System.out.println("Signed in to " + username + ".");
                    var uri = new URI("https://api.minecraftservices.com/minecraft/profile/name/" + snipedUsername);
                    snipeRequest = HttpRequest.newBuilder().uri(uri).header("Authorization", "Bearer " + authToken)
                            .PUT(HttpRequest.BodyPublishers.noBody()).build();
                    System.out.println("Setup complete!");
                    timer1.cancel();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        };
        if (new Date().before(authenticationTime))
            timer1.schedule(authentication, authenticationTime);
        else {
            System.out.println("Signed in to " + username + ".");
            var uri = new URI("https://api.minecraftservices.com/minecraft/profile/name/" + snipedUsername);
            snipeRequest = HttpRequest.newBuilder().uri(uri).header("Authorization", "Bearer " + authToken)
                    .PUT(HttpRequest.BodyPublishers.noBody()).build();
            System.out.println("Setup complete!");
        }
        timer2.schedule(snipe, delayAdjustedTime);
    }

    @Override
    public void isNameChangeEligible() throws URISyntaxException, IOException, InterruptedException {
        var uri = new URI("https://api.minecraftservices.com/minecraft/profile/namechange");
        var request = HttpRequest.newBuilder().uri(uri).header("Authorization", "Bearer " + authToken).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (!(response.statusCode() == 200))
            throw new GeneralSniperException(
                    "[NameChangeEligibilityChecker] HTTP status code: " + response.statusCode());
        var body = response.body();
        var node = mapper.readTree(body);
        boolean isAllowed = node.get("nameChangeAllowed").asBoolean();
        if (!isAllowed)
            throw new GeneralSniperException(
                    "[NameChangeEligibilityChecker] You cannot name change within the cooldown period.");
    }

    @Override
    public void checkNameAvailabilityTime() throws URISyntaxException, IOException, InterruptedException {
        var uri = new URI("https://api.kqzz.me/api/namemc/droptime/" + snipedUsername);
        var request = HttpRequest.newBuilder().uri(uri).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (!(response.statusCode() == 200))
            throw new GeneralSniperException("[CheckNameAvailabilityTime] HTTP status code: " + response.statusCode());
        var body = response.body();
        var node = mapper.readTree(body);
        try {
            dropTime = Instant.ofEpochSecond(node.get("droptime").asInt());
        } catch (NullPointerException ex) {
            turboSnipe = true;
        }
    }

    @Override
    public boolean isSecurityQuestionsNeeded() throws URISyntaxException, IOException, InterruptedException {
        var uri = new URI("https://api.mojang.com/user/security/location");
        var request = HttpRequest.newBuilder().uri(uri).header("Authorization", "Bearer " + authToken).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.discarding());
        switch (response.statusCode()) {
        case 204:
            return false;
        case 403:
            return true;
        default:
            throw new GeneralSniperException("[SecurityQuestionsCheck] HTTP status code: " + response.statusCode());
        }
    }

    @Override
    public void sendSecurityQuestions() throws URISyntaxException, IOException, InterruptedException {
        var postJSON = "[{\"id\":" + questionIDArray[0] + ",\"answer\":\"" + sq1 + "\"},{\"id\":" + questionIDArray[1]
                + ",\"answer\":\"" + sq2 + "\"},{\"id\":" + questionIDArray[2] + ",\"answer\":\"" + sq3 + "\"}]";
        var uri = new URI("https://api.mojang.com/user/security/location");
        var request = HttpRequest.newBuilder().uri(uri).header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.ofString(postJSON)).build();
        var response = client.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() == 403)
            throw new GeneralSniperException(
                    "[SendSecurityQuestions] Authentication error. Check if you have entered your security questions correctly.");
        if (!(response.statusCode() == 204))
            throw new GeneralSniperException("[SendSecurityQuestions] HTTP status code: " + response.statusCode());
    }

    @Override
    public boolean getSecurityQuestionsID() throws URISyntaxException, IOException, InterruptedException {
        var uri = new URI("https://api.mojang.com/user/security/challenges");
        var request = HttpRequest.newBuilder().uri(uri).header("Authorization", "Bearer " + authToken).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (!(response.statusCode() == 200))
            throw new GeneralSniperException("[GetSecurityQuestions] HTTP status code: " + response.statusCode());
        var body = response.body();
        if (body.equals("[]"))
            return false;
        var node = mapper.readTree(body);
        int count = 0;
        for (var innerNode : node) {
            questionIDArray[count] = innerNode.get("answer").get("id").asText();
            count++;
        }
        return true;
    }

    @Override
    public void authenticate() throws URISyntaxException, IOException, InterruptedException {
        var postJSON = "{\"agent\":{\"name\":\"Minecraft\",\"version\":1},\"username\":\"" + username
                + "\",\"password\":\"" + password
                + "\",\"clientToken\":\"Mojang-API-Client\",\"requestUser\":\"true\"}";
        var uri = new URI("https://authserver.mojang.com/authenticate");
        var request = HttpRequest.newBuilder().uri(uri)
                .headers("User-Agent", "X-Clacks-Overhead: GNU Terry Pratchett", "Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(postJSON)).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 403)
            throw new GeneralSniperException(
                    "[Authentication] Authentication error. Check if you have entered your username and password correctly.");
        if (!(response.statusCode() == 200))
            throw new GeneralSniperException("[Authentication] HTTP status code: " + response.statusCode());
        var body = response.body();
        var node = mapper.readTree(body);
        authToken = node.get("accessToken").asText();
    }
}
