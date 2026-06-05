package com.example.screenplay.service;

import com.example.screenplay.model.Chapter;
import com.example.screenplay.model.Screenplay;

import java.util.List;

public interface ScreenplayGenerator {

    Screenplay generate(String title, String format, List<Chapter> chapters);

    String mode();
}
