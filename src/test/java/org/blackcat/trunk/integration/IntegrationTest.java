package org.blackcat.trunk.integration;

import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.blackcat.trunk.verticles.MainVerticle;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;


@RunWith(VertxUnitRunner.class)
public class IntegrationTest {

    static final String baseUrl = "https://localhost:8080";

    static final Logger logger = LoggerFactory.getLogger(IntegrationTest.class);

    static private WebDriver driver;
    private Vertx vertx;

    @Rule
    public final RunTestOnContext rule = new RunTestOnContext(Vertx::vertx);

    @BeforeClass
    static public void init() {
        ChromeOptions options = new ChromeOptions();
        options.setHeadless(true);
        options.setAcceptInsecureCerts(true);
        options.addArguments("test-type");
        options.addArguments("headless");
        options.addArguments("--disable-web-security");
        options.addArguments("--allow-running-insecure-content");
        options.addArguments("--allow-insecure-localhost");
        options.addArguments("--reduce-security-for-testing");
        driver = new ChromeDriver(options);
    }

    @AfterClass
    static public void done(TestContext context) {
        driver.close();
    }

    @Test(timeout=5000)
    public void publicIndex(TestContext context) {
        Async async = context.async();
        deployForTesting(rule.vertx(), done -> {
            driver.get(baseUrl);
            WebElement h1 = driver.findElement(By.xpath("/html/body/div/div[1]/h1"));

            context.assertEquals("Trunk", h1.getText());
            async.complete();
        });
    }

    @Test(timeout=5000)
    public void privateIndex(TestContext context) {
        Async async = context.async();
        deployForTesting(rule.vertx(), done -> {
            driver.get(baseUrl);

            WebElement openLink = driver.findElement(By.xpath("/html/body/div/div[1]/a"));
            openLink.click();

            adminLogin();

            WebElement loggedUser = driver.findElement(By.xpath("/html/body/div/div[1]/div[1]/ul/li/div/span/a/strong"));
            context.assertEquals("admin@myhost.co", loggedUser.getText());
            async.complete();
        });
    }

    @Test(timeout=5000)
    public void logout(TestContext context) {
        Async async = context.async();
        deployForTesting(rule.vertx(), done -> {
            driver.get(baseUrl);

            WebElement openLink = driver.findElement(By.xpath("/html/body/div/div[1]/a"));
            openLink.click();

            adminLogin();

            WebElement logoutLink = driver.findElement(By.xpath("/html/body/div/div[2]/div[2]/div[2]/div/a/span"));
            logoutLink.click();

            WebElement h1 = driver.findElement(By.xpath("/html/body/div/div[1]/h1"));
            context.assertEquals("Trunk", h1.getText());
            async.complete();
        });
    }

    @Test(timeout=5000)
    public void createCollection(TestContext context) {
        Async async = context.async();
        deployForTesting(rule.vertx(), done -> {
            driver.get(baseUrl);

            WebElement openLink = driver.findElement(By.xpath("/html/body/div/div[1]/a"));
            openLink.click();
            adminLogin();

            WebElement plusButton = driver.findElement(By.xpath("//*[@id=\"plus-button\"]"));
            plusButton.click();

            new WebDriverWait(driver, 2)
                .until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("/html/body/div[1]/div[1]/div[5]/div")));
            WebElement nameInput = driver.findElement(By.xpath("//*[@id=\"additional-collection-resource-input\"]"));
            nameInput.sendKeys("whatever");

            WebElement commitButton = driver.findElement(By.xpath("//*[@id=\"commit-additional-collection-resource\"]"));
            commitButton.click();

            WebElement firstCollection = driver.findElement(By.xpath("/html/body/div/div[1]/div[3]/ul/li"));

            String text = firstCollection.getText().split("\n")[0];
            context.assertEquals("whatever", text);
            async.complete();
        });
    }

    private void deployForTesting(Vertx vertx, Handler<AsyncResult<String>> done) {
        vertx.fileSystem().deleteRecursive("/tmp/trunk", true, _1-> {
            vertx.fileSystem().mkdir("/tmp/trunk", _2 -> {
                DeploymentOptions deploymentOptions = new DeploymentOptions();
                deploymentOptions.setConfig(getConfiguration());
                vertx.deployVerticle(MainVerticle.class.getName(), deploymentOptions, done);
            });
        });
    }

    private void adminLogin() {
        WebElement username = driver.findElement(By.id("username"));
        username.sendKeys("admin");

        WebElement password = driver.findElement(By.id("password"));
        password.sendKeys("admin");

        WebElement loginButton = driver.findElement(By.id("kc-login"));
        loginButton.click();
    }

    private static JsonObject getConfiguration() {
        byte[] bytes = new byte[0];
        try {
            bytes = Files.readAllBytes(Paths.get("conf/config.json"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new JsonObject(new String(bytes));
    }
}
