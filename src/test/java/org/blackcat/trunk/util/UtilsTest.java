package org.blackcat.trunk.util;


import io.vertx.core.json.JsonArray;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

public class UtilsTest {

  @Test
  public void humanReadableByteCount() {
    final String one_kilobyte_si = Utils.humanReadableByteCount(1024);
    assertEquals("1.0 kB", one_kilobyte_si);

    final String one_megabite_si = Utils.humanReadableByteCount(1024 * 1024);
    assertEquals("1.0 MB", one_megabite_si);

    final String one_gigabite_si = Utils.humanReadableByteCount(1024 * 1024 * 1024);
    assertEquals("1.0 GB", one_gigabite_si);
  }

  @Test
  public void urlDecode() {
    final String decoded = Utils.urlDecode("http%3A%2F%2Flocalhost%3A8080%2F");
    assertEquals("http://localhost:8080/", decoded);
  }

  @Test
  public void urlEncode() {
    final String encoded = Utils.urlEncode("http://localhost:8080/");
    assertEquals("http%3A%2F%2Flocalhost%3A8080%2F", encoded);
  }

  @Test
  public void makeTempFileName() {
    String tmp1 = Utils.makeTempFileName("abc");
    String tmp2 = Utils.makeTempFileName("abc");

    assertFalse(tmp1.isEmpty());
    assertFalse(tmp2.isEmpty());
    assertFalse(tmp1.equals(tmp2));
  }

  @Test
  public void isValidEmail() {
    checkIsValidEmail("john.doe@gmail.com");
    checkIsValidEmail("guyfawkes@yahoo.co.uk");

    checkIsInvalidEmail("donald");
    checkIsInvalidEmail("www.cnn.com");
  }

  private void checkIsInvalidEmail(String email) {
    assertFalse(Utils.isValidEmail(email));
  }

  private void checkIsValidEmail(String email) {
    assertTrue(Utils.isValidEmail(email));
  }

  @Test
  public void listToJsonArray() {
      List<Integer> integers = Arrays.asList(0, 1, 2, 3);
      JsonArray objects = Utils.listToJsonArray(integers);
      Assert.assertThat(objects, is(new JsonArray("[0,1,2,3]")));
  }
}