.PHONY: commit build clean install help status add push day

# Colors for output
RED := \033[0;31m
GREEN := \033[0;32m
YELLOW := \033[1;33m
BLUE := \033[0;34m
NC := \033[0m

# Get current day of week (1=Monday, 5=Friday, 7=Sunday)
DAY_OF_WEEK := $(shell date +%u)
DAY_NAME := $(shell date +%A)

# App configuration
APP_NAME := ongoma-v2
APK_PATH := build/outputs/apk/debug/$(APP_NAME)-debug.apk

help:
	@echo "$(BLUE)╔═══════════════════════════════════════════════════════╗$(NC)"
	@echo "$(BLUE)║          Ongoma V2 - Makefile Commands               ║$(NC)"
	@echo "$(BLUE)╚═══════════════════════════════════════════════════════╝$(NC)"
	@echo ""
	@echo "$(YELLOW)Git Commands (FRIDAY ONLY):$(NC)"
	@echo "  make commit       - Commit changes (prompts for message, -s auto)"
	@echo "  make push         - Push to remote"
	@echo "  make add          - Stage all changes"
	@echo "  make status       - Show git status"
	@echo ""
	@echo "$(YELLOW)Build Commands (Any Day):$(NC)"
	@echo "  make build        - Build debug APK"
	@echo "  make clean        - Clean build artifacts"
	@echo "  make install      - Install APK to device"
	@echo ""
	@echo "$(GREEN)Current Day: $(DAY_NAME)$(NC)"
	@if [ "$(DAY_OF_WEEK)" = "5" ]; then \
		echo "$(GREEN)✓ Commits are ALLOWED today!$(NC)"; \
	else \
		echo "$(RED)✗ Commits NOT allowed (Friday only)$(NC)"; \
	fi
	@echo ""

# Stage all changes
add:
	@echo "$(BLUE)➜ Staging changes...$(NC)"
	@git add .
	@echo "$(GREEN)✓ Changes staged$(NC)"
	@git status --short

# Show git status
status:
	@echo "$(BLUE)➜ Git Status:$(NC)"
	@git status

# Commit with Friday restriction and signature
commit:
	@if [ "$(DAY_OF_WEEK)" != "5" ]; then \
		echo ""; \
		echo "$(RED)════════════════════════════════════════════════════$(NC)"; \
		echo "$(RED)✗ COMMITS ARE ONLY ALLOWED ON FRIDAYS!$(NC)"; \
		echo "$(RED)════════════════════════════════════════════════════$(NC)"; \
		echo ""; \
		echo "$(YELLOW)Today is $(DAY_NAME). Please come back on Friday.$(NC)"; \
		echo ""; \
		exit 1; \
	fi
	@echo ""
	@echo "$(GREEN)✓ Today is Friday! You can commit.$(NC)"
	@echo ""
	@read -p "$$(echo -e '$(YELLOW)Enter commit message: $(NC)')" msg; \
	if [ -z "$$msg" ]; then \
		echo "$(RED)✗ Commit message cannot be empty$(NC)"; \
		exit 1; \
	fi; \
	echo ""; \
	echo "$(BLUE)➜ Staging all changes...$(NC)"; \
	git add .; \
	echo "$(BLUE)➜ Creating signed commit...$(NC)"; \
	git commit -m "$$msg" -s; \
	echo ""; \
	echo "$(GREEN)════════════════════════════════════════════════════$(NC)"; \
	echo "$(GREEN)✓ Commit successful!$(NC)"; \
	echo "$(GREEN)════════════════════════════════════════════════════$(NC)"; \
	echo ""; \
	echo "$(YELLOW)Use 'make push' to push to remote$(NC)"; \
	echo ""

# Push with Friday restriction
push:
	@if [ "$(DAY_OF_WEEK)" != "5" ]; then \
		echo ""; \
		echo "$(RED)════════════════════════════════════════════════════$(NC)"; \
		echo "$(RED)✗ PUSH IS ONLY ALLOWED ON FRIDAYS!$(NC)"; \
		echo "$(RED)════════════════════════════════════════════════════$(NC)"; \
		echo ""; \
		echo "$(YELLOW)Today is $(DAY_NAME). Please come back on Friday.$(NC)"; \
		echo ""; \
		exit 1; \
	fi
	@echo "$(GREEN)✓ Today is Friday! Pushing...$(NC)"
	@git push
	@echo "$(GREEN)✓ Push successful!$(NC)"

# Build debug APK (any day)
build:
	@echo "$(BLUE)➜ Building debug APK...$(NC)"
	@./build.sh debug
	@echo "$(GREEN)✓ Build complete!$(NC)"

# Clean build artifacts (any day)
clean:
	@echo "$(BLUE)➜ Cleaning build artifacts...$(NC)"
	@./build.sh clean
	@echo "$(GREEN)✓ Clean complete!$(NC)"

# Install APK to device (any day)
install:
	@if [ ! -f "ongoma.apk" ]; then \
		echo "$(RED)✗ ongoma.apk not found. Run 'make build' first.$(NC)"; \
		exit 1; \
	fi
	@echo "$(BLUE)➜ Installing APK to device...$(NC)"
	@adb install -r ongoma.apk
	@echo "$(GREEN)✓ Installation complete!$(NC)"

# Show current day (for testing)
day:
	@echo ""
	@echo "$(BLUE)════════════════════════════════════════════════════$(NC)"
	@echo "$(YELLOW)Today is: $(DAY_NAME) (Day $(DAY_OF_WEEK) of week)$(NC)"
	@echo "$(BLUE)════════════════════════════════════════════════════$(NC)"
	@echo ""
	@if [ "$(DAY_OF_WEEK)" = "5" ]; then \
		echo "$(GREEN)✓ Commits and pushes are ALLOWED today!$(NC)"; \
	else \
		echo "$(RED)✗ Commits and pushes are NOT allowed today.$(NC)"; \
		echo "$(YELLOW)Please wait until Friday.$(NC)"; \
	fi
	@echo ""
