# SpeedMatch-Game
SpeedMatch is an interactive reaction-time game that connects a Java desktop interface with an Arduino 7-segment display. The goal is simple — test how quickly you can recognize and respond to random numbers.

When the game starts, a random digit appears on the 7-segment display.
The player must quickly select the same number on the Java interface.
The program records the user’s response time across three rounds and then calculates the average reaction speed in seconds.

Java (Swing GUI) – for building the user interface and timing logic.
Arduino UNO + 7-Segment Display – for showing random numbers.
jSerialComm Library – for real-time serial communication between Java and Arduino.
