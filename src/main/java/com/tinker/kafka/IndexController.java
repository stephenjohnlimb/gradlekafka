package com.tinker.kafka;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** We have to add this in and the spring web stuff so that actuator can actually run. */
@RestController
public class IndexController {

  @GetMapping("/")
  public String index()
  {
    return "Running";
  }
}
