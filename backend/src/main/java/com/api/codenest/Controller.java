package com.api.codenest;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import javax.tools.SimpleJavaFileObject;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin(maxAge = 3600)
@RestController
@RequestMapping("/api/code")
public class Controller {

	@Autowired
	private CodeExecutorService codeSummaryService;

	private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

	@PostMapping("/execute")
	public ResponseEntity<String> executeCode(@RequestParam String language, @RequestParam String code,
			@RequestParam String input) {

		System.out.println("Execute code called for language " + language);
		String id = generateRandomString(5);
		// Save the language and code into MySQL
		try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/codenest", "root", "root")) {
			String sql = "INSERT INTO codesummary (id, language, code, input) VALUES (?, ?, ?, ?)";
			try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
				pstmt.setString(1, id);
				pstmt.setString(2, language);
				pstmt.setString(3, code);
				pstmt.setString(4, input);
				pstmt.executeUpdate();
			}
		} catch (SQLException e) {
			e.printStackTrace();
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error saving code to database");
		}

		CompletableFuture.runAsync(() -> {

			switch (language.toLowerCase()) {
			case "java":
				executeJavaCode(id);
				break;
			case "python":
				executePythonCode(id);
				break;
			case "cpp":
				executeCppCode(id);
				break;
			case "javascript":
				executeJavaScriptCode(id);
				break;
			// Add cases for other languages as needed
			}
		});
		return ResponseEntity.ok(id);
	}

	@CrossOrigin(origins = "*")
	@GetMapping("/output")
	public ResponseEntity<String> getOutput(@RequestParam String id) {
		Optional<CodeSummaryEntity> entity = codeSummaryService.getFieldById(id);
		if (entity.isEmpty()) {
			return ResponseEntity.badRequest().build();
		}
		String output = entity.get().getOutput();
		if (output == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(output);
	}

	static class StringSourceJavaObject extends SimpleJavaFileObject {
		private final String code;
		private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

		public StringSourceJavaObject(String name, String code) {
			super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
			this.code = code;
		}

		@Override
		public CharSequence getCharContent(boolean ignoreEncodingErrors) {
			return code;
		}

		public String getOutput() {
			return outputStream.toString();
		}

		@Override
		public OutputStream openOutputStream() {
			return outputStream;
		}
	}

	// java code
	private void executeJavaCode(String id) {
		String output;
		try {
			Optional<CodeSummaryEntity> res = codeSummaryService.getFieldById(id);
			String input = res.get().getInput();

			// Write the Java code to a file
			Path sourcePath = Paths.get("Main.java");
			Files.write(sourcePath, res.get().getCode().getBytes());

//			// Compile the Java code
//			Process compileProcess = new ProcessBuilder("java", "-c").start();
//			compileProcess.waitFor();

			// Execute the compiled Java code with input
			ProcessBuilder processBuilder = new ProcessBuilder("java", sourcePath.toString());
			Process executeProcess = processBuilder.start();
			try (OutputStream outputStream = executeProcess.getOutputStream()) {
				outputStream.write(input.getBytes());
			}

			BufferedReader reader = new BufferedReader(new InputStreamReader(executeProcess.getInputStream()));
			StringBuilder result = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				result.append(line).append("\n");
			}

			// Wait for the process to complete and capture any errors
			int exitCode = executeProcess.waitFor();
			if (exitCode == 0) {
				output = result.toString();
			} else {
				BufferedReader errorReader = new BufferedReader(new InputStreamReader(executeProcess.getErrorStream()));
				StringBuilder errorResult = new StringBuilder();
				String errorLine;
				while ((errorLine = errorReader.readLine()) != null) {
					errorResult.append(errorLine).append("\n");
				}
				output = "Error executing Java code:\n" + errorResult.toString();
			}
		} catch (Exception e) {
			output = "Error executing Java code:\n" + e.toString();
		}
		codeSummaryService.updateFieldById(id, output);
	}

	public static String generateRandomString(int length) {
		Random random = new Random();
		StringBuilder sb = new StringBuilder(length);

		for (int i = 0; i < length; i++) {
			int randomIndex = random.nextInt(CHARACTERS.length());
			char randomChar = CHARACTERS.charAt(randomIndex);
			sb.append(randomChar);
		}

		return sb.toString();
	}

	// python code
	private void executePythonCode(String id) {
		String output;
		try {
			Optional<CodeSummaryEntity> res = codeSummaryService.getFieldById(id);
			String input = res.get().getInput();

			Path sourcePath = Paths.get("main.py");
			Files.write(sourcePath, res.get().getCode().getBytes());

			// Execute the Python code with input
			ProcessBuilder processBuilder = new ProcessBuilder("python", sourcePath.toString());
			Process process = processBuilder.start();

			// Pass input to the process
			try (OutputStream outputStream = process.getOutputStream()) {
				outputStream.write(input.getBytes());
			}

			// Read the output of the Python script
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			StringBuilder result = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				result.append(line).append("\n");
			}

			// Wait for the process to complete and capture any errors
			int exitCode = process.waitFor();
			if (exitCode == 0) {
				output = result.toString();
			} else {
				BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
				StringBuilder errorResult = new StringBuilder();
				String errorLine;
				while ((errorLine = errorReader.readLine()) != null) {
					errorResult.append(errorLine).append("\n");
				}
				output = "Error executing Python code:\n" + errorResult.toString();
			}
		} catch (Exception e) {
			output = "Error executing Python code:\n" + e.toString();
		}
		codeSummaryService.updateFieldById(id, output);
	}

	// cpp code
	private void executeCppCode(String id) {
		String output;
		try {
			Optional<CodeSummaryEntity> res = codeSummaryService.getFieldById(id);
			String input = res.get().getInput();

			// Write the C++ code to a file
			Path sourcePath = Paths.get("main.cpp");
			Files.write(sourcePath, res.get().getCode().getBytes());

			// Compile the C++ code
			Process compileProcess = new ProcessBuilder("g++", sourcePath.toString(), "-o", "main").start();
			compileProcess.waitFor();

			// Execute the compiled C++ code with input
			ProcessBuilder processBuilder = new ProcessBuilder("./main");
			Process executeProcess = processBuilder.start();
			try (OutputStream outputStream = executeProcess.getOutputStream()) {
				outputStream.write(input.getBytes());
			}

			BufferedReader reader = new BufferedReader(new InputStreamReader(executeProcess.getInputStream()));
			StringBuilder result = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				result.append(line).append("\n");
			}

			// Wait for the process to complete and capture any errors
			int exitCode = executeProcess.waitFor();
			if (exitCode == 0) {
				output = result.toString();
			} else {
				BufferedReader errorReader = new BufferedReader(new InputStreamReader(executeProcess.getErrorStream()));
				StringBuilder errorResult = new StringBuilder();
				String errorLine;
				while ((errorLine = errorReader.readLine()) != null) {
					errorResult.append(errorLine).append("\n");
				}
				output = "Error executing C++ code:\n" + errorResult.toString();
			}
		} catch (Exception e) {
			output = "Error executing C++ code:\n" + e.toString();
		}
		codeSummaryService.updateFieldById(id, output);
	}

	// js code
	private void executeJavaScriptCode(String id) {
		String output;
		try {
			// Store the JavaScript code temporarily in a file (same as before)
			Optional<CodeSummaryEntity> res = codeSummaryService.getFieldById(id);
			String input = res.get().getInput();
			Path scriptPath = Paths.get("main.js");
			Files.write(scriptPath, res.get().getCode().getBytes());

			// Use Node.js to execute the script with input
			ProcessBuilder processBuilder = new ProcessBuilder("node", scriptPath.toString());
			Process process = processBuilder.start();
			try (OutputStream outputStream = process.getOutputStream()) {
				outputStream.write(input.getBytes());
			}

			// Capture output
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			StringBuilder result = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				result.append(line).append("\n");
			}
			output = result.toString();

			// Handle errors
			int exitCode = process.waitFor();
			if (exitCode != 0) {
				BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
				StringBuilder errorResult = new StringBuilder();
				String errorLine;
				while ((errorLine = errorReader.readLine()) != null) {
					errorResult.append(errorLine).append("\n");
				}
				output = "Error executing JavaScript code:\n" + errorResult.toString();
			}
		} catch (Exception e) {
			output = "Error executing JavaScript code:\n" + e.toString();
		}
		codeSummaryService.updateFieldById(id, output);
	}

}
