package org.blackcat.trunk.integration;

import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.blackcat.trunk.verticles.MainVerticle;
import org.junit.After;
import org.junit.Before;
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
    static final String testUserEmail = "admin@myhost.co";
    static final String rootPath = "/tmp/trunk";

    static final String loggedUserXPath = "/html/body/div/div[1]/div[1]/ul/li/div/span/a/strong";
    static final String homePageHeaderXPath = "/html/body/div/div[1]/h1";
    static final String logoutLinkXPath = "/html/body/div/div[2]/div[2]/div[2]/div/a/span";
    static final String plusButtonXPath = "//*[@id=\"plus-button\"]";
    static final String commitButtonXPath = "//*[@id=\"commit-additional-collection-resource\"]";
    static final String firstCollectionXPath = "/html/body/div/div[1]/div[3]/ul/li";
    static final String openLinkXPath = "/html/body/div/div[1]/a";
    static final String usernameInputXPath = "//*[@id=\"username\"]";
    static final String passwordInputXPath = "//*[@id=\"password\"]";
    static final String loginButtonXPath = "//*[@id=\"kc-login\"]";
    static final String loginFormXPath = "//*[@id=\"kc-form-login\"]";
    static final String additionalCollectionNameInputXPath = "//*[@id=\"additional-collection-resource-input\"]";

    private WebDriver driver;

    @Rule
    public final RunTestOnContext rule = new RunTestOnContext(Vertx::vertx);

    @Before
    public void init() {
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

    @After
    public void done(TestContext context) {
        driver.close();
    }

    @Test(timeout=5000)
    public void publicIndex(TestContext context) {
        Async async = context.async();
        deployForTesting(rule.vertx(), done -> {
            driver.get(baseUrl);

            WebElement h1 = driver.findElement(By.xpath(homePageHeaderXPath));

            context.assertEquals("Trunk", h1.getText());
            async.complete();
        });
    }

    @Test(timeout=5000)
    public void privateIndex(TestContext context) {
        Async async = context.async();
        deployForTesting(rule.vertx(), done -> {
            adminLogin();

            WebElement loggedUser = driver.findElement(By.xpath(loggedUserXPath));
            context.assertEquals(testUserEmail, loggedUser.getText());

            async.complete();
        });
    }

    @Test(timeout=5000)
    public void logout(TestContext context) {
        Async async = context.async();
        deployForTesting(rule.vertx(), done -> {
            adminLogin();

            WebElement logoutLink = driver.findElement(By.xpath(logoutLinkXPath));
            logoutLink.click();

            WebElement h1 = driver.findElement(By.xpath(homePageHeaderXPath));
            context.assertEquals("Trunk", h1.getText());

            async.complete();
        });
    }

    @Test(timeout=5000)
    public void createCollection(TestContext context) {
        Async async = context.async();
        deployForTesting(rule.vertx(), done -> {
            adminLogin();

            WebElement plusButton = driver.findElement(By.xpath(plusButtonXPath));
            plusButton.click();

            new WebDriverWait(driver, 2)
                .until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("/html/body/div[1]/div[1]/div[5]/div")));
            WebElement nameInput = driver.findElement(By.xpath(additionalCollectionNameInputXPath ));
            nameInput.sendKeys("whatever");

            WebElement commitButton = driver.findElement(By.xpath(commitButtonXPath));
            commitButton.click();

            WebElement firstCollection = driver.findElement(By.xpath(firstCollectionXPath));

            String text = firstCollection.getText().split("\n")[0];
            context.assertEquals("whatever", text);

            async.complete();
        });
    }

    private void deployForTesting(Vertx vertx, Handler<AsyncResult<String>> done) {
        vertx.fileSystem().deleteRecursive(rootPath, true, _1-> {
            vertx.fileSystem().mkdir(rootPath, _2 -> {
                DeploymentOptions deploymentOptions = new DeploymentOptions();
                deploymentOptions.setConfig(getConfiguration());
                vertx.deployVerticle(MainVerticle.class.getName(), deploymentOptions, done);
            });
        });
    }

    private void adminLogin() {
        driver.get(baseUrl);

        WebElement openLink = driver.findElement(By.xpath(openLinkXPath));
        openLink.click();

        new WebDriverWait(driver, 2)
            .until(ExpectedConditions.visibilityOfElementLocated(By.xpath(loginFormXPath)));

        WebElement username = driver.findElement(By.xpath(usernameInputXPath));
        username.sendKeys("admin");

        WebElement password = driver.findElement(By.xpath(passwordInputXPath));
        password.sendKeys("admin");

        WebElement loginButton = driver.findElement(By.xpath(loginButtonXPath));
        loginButton.submit();
    }

    private static JsonObject getConfiguration() {
        try {
            byte[] bytes = Files.readAllBytes(Paths.get("conf/config.json"));
            return new JsonObject(new String(bytes));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
