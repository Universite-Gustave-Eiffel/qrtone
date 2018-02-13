package org.noise_planet.openwarble;

import org.junit.Test;
import org.noise_planet.openwarble.OpenWarble;
import java.lang.reflect.*;

import static org.hamcrest.Matchers.closeTo;
import static org.junit.Assert.assertThat;

/**
 * Example of using a compiled C function from Java
 */
public class YinACF_Test {

  @Test
  public void constructorTest() {

    try {
        Class c = OpenWarble.class;
        Field[] f = c.getDeclaredFields();
        System.out.println("Fields:");
        for (Field aF : f) System.out.println(aF.toString());
        Method[] m = c.getDeclaredMethods();
        System.out.println("Methods:");
        for (Method aM : m) System.out.println(aM.toString());
    } catch (Throwable e) {
        System.err.println(e);
    }
  }
  
}
