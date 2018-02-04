package org.blackcat.trunk.integration;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.Timeout;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

@RunWith(VertxUnitRunner.class)
public class IntegrationTest {

    static private Vertx vertx;
    static private WebDriver driver;

    @Rule
    public Timeout globalTimeout = new Timeout(10, TimeUnit.SECONDS);

    @BeforeClass
    static public void init(TestContext context) {
        ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.setHeadless(true);
        chromeOptions.addArguments("--silent");

        driver = new ChromeDriver(chromeOptions);

        DeploymentOptions options = new DeploymentOptions();
        options.setConfig(getConfiguration(context));

        vertx = Vertx.vertx();
        vertx.deployVerticle(MainVerticle.class.getName(), options, context.asyncAssertSuccess());
    }

    private static JsonObject getConfiguration(TestContext context) {
        try {
            byte[] bytes = Files.readAllBytes(Paths.get("conf/config.json"));
            return new JsonObject(new String(bytes));
        } catch (IOException e) {
            context.fail("No configuration");
            return null;
        }
    }

    @AfterClass
    static public void done(TestContext context) {
        driver.close();
        vertx.close(context.asyncAssertSuccess());
    }

    @Test
    public void publicIndex(TestContext context) {
        String baseUrl = "http://localhost:8080/";
        driver.get(baseUrl);

        WebElement h1 = driver.findElement(By.xpath("/html/body/div/div[1]/h1"));
        context.assertEquals("Trunk", h1.getText());
        context.async().complete();
    }

    @Test
    public void privateIndex(TestContext context) {
        String baseUrl = "http://localhost:8080/";
        driver.get(baseUrl);

        WebElement openLink = driver.findElement(By.xpath("/html/body/div/div[1]/a"));
        openLink.click();

        adminLogin();

        WebElement loggedUser = driver.findElement(By.xpath("/html/body/div/div[1]/div[1]/ul/li/div/span/a/strong"));
        context.assertEquals("admin@myhost.co", loggedUser.getText());

        context.async().complete();
    }

    @Test
    public void logout(TestContext context) {
        String baseUrl = "http://localhost:8080/";
        driver.get(baseUrl);

        WebElement openLink = driver.findElement(By.xpath("/html/body/div/div[1]/a"));
        openLink.click();

        adminLogin();

        WebElement logoutLink = driver.findElement(By.xpath("/html/body/div/div[2]/div[2]/div[2]/div/a/span"));
        logoutLink.click();

        WebElement h1 = driver.findElement(By.xpath("/html/body/div/div[1]/h1"));
        context.assertEquals("Trunk", h1.getText());
        context.async().complete();
    }

    private void adminLogin() {
        WebElement username = driver.findElement(By.id("username"));
        username.sendKeys("admin");

        WebElement password = driver.findElement(By.id("password"));
        password.sendKeys("admin");

        WebElement loginButton = driver.findElement(By.id("kc-login"));
        loginButton.click();
    }
}
