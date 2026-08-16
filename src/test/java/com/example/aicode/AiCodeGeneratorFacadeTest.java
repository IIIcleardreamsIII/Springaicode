package com.example.aicode;

import com.example.aicode.ai.model.enums.CodeGenTypeEnum;
import com.example.aicode.core.AiCodeGeneratorFacade;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;

@SpringBootTest
class AiCodeGeneratorFacadeTest {

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;


}

