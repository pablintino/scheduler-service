package com.pablintino.schedulerservice.helpers;

import com.pablintino.schedulerservice.callback.CallbackMessage;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@RestController
@RequestMapping("/test")
public class HttpCallbackMockController {

  @Getter private BlockingQueue<CallbackMessage> messageQueue = new LinkedBlockingQueue<>();

  @PostMapping("success")
  public ResponseEntity<Void> successPost(@RequestBody CallbackMessage callbackMessage) {
    messageQueue.add(callbackMessage);
    return new ResponseEntity<>(HttpStatus.OK);
  }
}
