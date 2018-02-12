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
    
    OpenWarble p = new OpenWarble();

    try {
        Class c = OpenWarble.class;
        Method[] m = c.getDeclaredMethods();
        for (int i = 0; i < m.length; i++)
        System.out.println(m[i].toString());
    } catch (Throwable e) {
        System.err.println(e);
    }
  }
  
}
