---
title: MyFoodTracker Product Brief
status: complete
created: 2026-08-02
updated: 2026-08-02
---

# Product Brief: MyFoodTracker

## Executive Summary

MyFoodTracker is a lightweight, privacy-first mobile application designed to help users log their food and water intakes, track macronutrients, and monitor historical progress. Unlike modern diet tracking applications that are heavily commercialized and slow, MyFoodTracker places user sovereignty and performance first, storing all data in a local, secure SQLite database on the device.

The app is built for a small circle of family members and acquaintances (10–15 users maximum) who want a simple utility without ads, subscriptions, or invasive data collection. In addition to being a functional daily tool, MyFoodTracker serves as a clean, high-quality reference project for Kotlin Android development. It demonstrates robust MVVM architecture, Clean Architecture boundaries, Koin dependency injection, Room database persistence, and the BMAD Specification-Driven Development (SDD) paradigm.

By focusing on offline performance and instant feedback, the application lowers the friction of daily logging. It empowers users to take control of their nutritional health while maintaining absolute custody of their personal data.

## The Problem

- **Exploitative Monetization & Bloat**: Mainstream diet tracking apps have become unusable due to aggressive paywalls (e.g., locking barcode scanners or history views behind subscriptions), distracting ads, and heavy feature bloat (social feeds, upsells, and gamification).
- **Data Privacy Concerns**: Diet, weight, and water consumption data is deeply personal. Commercial health platforms routinely harvest, profile, and monetize this sensitive health information, sharing it with advertising networks and brokers.
- **Connectivity Reliance**: Existing trackers are slow to load and fail completely when offline or in poor reception areas because they rely on continuous connections to remote cloud services for search and sync.

## The Solution

- **Local-First Kotlin Android App**: Built with MVVM, Clean Architecture, Koin DI, and Room Database to keep all data locally isolated, fast, and secure.
- **Pre-Populated SQLite Food DB**: A built-in, indexed database of common foods (curated from public domain sources) utilizing SQLite FTS5 (Full-Text Search) for instantaneous, offline search autocomplete.
- **Dual-Layer Database**: Users search the built-in database first, but can create and save custom foods and meals to their personal local database.
- **Week & Month Navigation**: A daily tracking dashboard showing progress against macro goals (Calories, Protein, Carbs, Fat) with a horizontal **Week View** at the top for quick day-switching. A dedicated **Month View** screen allows navigating to deeper historical logs.
- **Simple Water Tracker**: A single-tap `+250ml` button on the dashboard for instant logging.
- **Data Portability**: An export tool that converts custom-period food and water logs into clean TXT or CSV tables.

## What Makes This Different

- **Zero Commercialization**: Completely ad-free, subscription-free, and designed with zero data-brokering.
- **Privacy by Default**: All logs, macro data, and personal details are stored exclusively in a local SQLite database. Any future syncing is opt-in and self-hosted.
- **Offline-First Speed**: Instant UI responses and full logging capabilities offline, with zero network calls required for core tracking.
- **Development Focus**: Built explicitly to demonstrate high-quality Kotlin Android development and robust Specification-Driven Development (SDD) practices under BMAD.

## Who This Serves

- **Primary Users**: A small, close-knit circle of family members and acquaintances (10–15 users maximum) running the app on individual devices.
- **Characteristics**: They desire a straightforward, fast diet tracking method without being commodified by ad networks. They value digital sovereignty and data privacy.

## Success Criteria

- **Learning Goal**: Complete implementation must strictly follow [project-context.md](file:///c:/Users/Xenae/Documents/source/repos/AndroidStudioProjects/MyFoodTracker/_bmad-output/project-context.md) conventions (Room synchronous DB access, Koin DI, domain mapping, decoupled layers).
- **Speed**: Search results return and logs save in under `50 ms` using local indexing.
- **Offline Independence**: `100%` of MVP functionality must work without cellular reception or internet connectivity.
- **Usability**: Log a meal in `<15` seconds (Open app → Search → Enter weight → Save).

## Scope

### In MVP (Phase 1)

- **Local User Profiles**: Password-protected profile creation. All data is kept isolated per-user in local tables.
- **Offline Search & Logging**: High-speed offline food search against pre-populated assets + weight-based logging.
- **Custom Food Manager**: Create and save personal foods/recipes with custom macro values.
- **Quick Water Log**: The `+250ml` tap button.
- **Week-View Dashboard**: Visual daily progress gauges (macros/calories) with a horizontal week-selector.
- **Month Calendar**: A navigation calendar to browse historical days.
- **Export Tool**: Generate plain text or CSV tables of log summaries.

### Out of MVP (Future Phases)

- **On-Device AI Classification**: Local camera-based meal classification and portion/weight estimation.
- **Smart Meal Configurations**: Saved combinations (e.g., "Standard Breakfast"), smart auto-suggestions, and search autocomplete.
- **Calendar Macro Badges ("Teasers")**: Showing actual macronutrient numbers directly inside the monthly calendar cells.
- **Period Tracker**: Menstrual cycle calendar tracking within the same database/view.
- **Physical Activity Logger**: Manual burnt calories input, followed by wearable device integration.
- **Cloud Sync**: Self-hosted or private cloud sync with cloud login.

## Vision

In the next 2-3 years, MyFoodTracker will evolve into a comprehensive, self-sovereign health companion:
- **Self-Hosted Cloud Ecosystem**: Simple, end-to-end encrypted synchronization with self-hosted cloud instances (such as Nextcloud or personal servers) to preserve privacy while supporting multiple devices.
- **Local AI Assistants**: Integrating lightweight, on-device machine learning models as they become commoditized to automatically classify meals from photos and estimate portion sizes completely offline.
- **Unified Health Logging**: Extending tracking capabilities to menstrual cycles, sleep, physical activity, and wearable integrations, consolidating them into a unified, beautiful, and completely private wellness dashboard.
