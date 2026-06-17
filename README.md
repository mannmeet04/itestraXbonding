# itestraXbonding

## Overview
This repository contains the source code for the autonomous Snake bot developed by team **mvm** during the 24-hour itestra x bonding Hackathon in Aachen. 

The challenge consisted of a real-time multiplayer Snake arena where autonomous agents competed against each other on a shared grid. 

## The Challenge
Unlike traditional Snake, this environment was highly dynamic:
* **Multiplayer Competition:** Competing against multiple other bots simultaneously in the same arena.
* **Dynamic Mechanics:** Adapting to changing rules, obstacles (e.g., skull apples), and varying grid states across multiple rounds.
* **Real-Time Execution:** Algorithms had to make movement decisions under strict time pressure to avoid timeouts or fatal collisions.

## Strategy & Architecture
Our core approach focused on aggressive growth and actively eliminating the competition to dominate the arena.
* **Aggressive Resource Acquisition:** Prioritizing rapid growth by actively hunting for apples on the grid to increase both length and score as fast as possible.
* **Offensive Maneuvering:** Deliberately cutting off opponents' paths and forcing collisions to eliminate competing snakes and reduce threats in the arena.
* **Calculated Interception:** Utilizing pathfinding algorithms not just for navigation, but to calculate interception vectors to trap opponents and secure resources.

## Results
* 🏆 **Top 5 Finish** in a highly competitive field of 11 teams.
* Built and deployed a robust autonomous system within exactly 24 hours.

## Team mvm
* Mannmeet Singh Khasria
* Vaishnavi Srinivas
