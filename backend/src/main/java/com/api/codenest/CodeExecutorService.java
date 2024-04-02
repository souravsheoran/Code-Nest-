package com.api.codenest;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;

@Service
public class CodeExecutorService {
	@Autowired
	private CodeSummaryRepository repository;

	@Transactional
	public void updateFieldById(String id, String output) {
		Optional<CodeSummaryEntity> optionalEntity = repository.findById(id);
		if (optionalEntity.isPresent()) {
			CodeSummaryEntity entity = optionalEntity.get();
			entity.setOutput(output);
			
			repository.save(entity);
		} else {
			// Handle case when entity with given ID is not found
		}
	}
	
	public Optional<CodeSummaryEntity> getFieldById(String id) {
		return repository.findById(id);
	}
}
