ESPORTS TOURNAMENT PROGRAM - README
ISTE 330 - Group 1 - Milestone 2


WHAT YOU NEED
  - MySQL 8 or newer (with MySQL Workbench)
  - Java 11 or newer


STEP 1 - MAKE THE DATABASE
  1. Open MySQL Workbench.
  2. Open the file  esports_tms.sql
  3. Click the lightning bolt.
  Done. The database is ready, with sample data inside.


STEP 2 - START THE PROGRAM
  Open a terminal (Command Prompt) in this folder and type:

      javac PresentationLayer.java
      java PresentationLayer

  You only compile PresentationLayer. DataLayer is compiled with it.
  A login window opens.


STEP 3 - LOG IN
  Director:   director1   password  Director123
  Coach:      coach1      password  Coach123
  Player:     player1     password  Player123

  New user? Click "Create an account".

Example player join, team, some examples of data entry and results. To show a process
that highlights the use of intersection and the system

THINGS TO TRY
  - The menu is on the left. Home shows the most important numbers.
  - Double-click any row to see who it is linked to:
    a team's players, a player's teams, the teams and players in a tournament,
    the players in a match.
  - Find (or the search box at the top) finds teams, players and tournaments.
  - Player: "Find a team" shows teams you could join.
    Pick one and click "Ask to join". Answers are on the "My requests" tab.
    Open a team to see its coach.
  - Player: My teams > "Leave team" takes you off a team.
  - Coach:  My teams > "Create a team" makes a new team that you coach.
  - Director: Teams > "Add a team" makes a team for any coach.
  - Coach:  "Join requests" shows players who want to join your teams.
    Click Approve or Decline.
  - Coach:  "Find players" shows players you could add to your team.
  - Coach:  Tournaments > "Drop out" takes your team out of a tournament.
  - Director: Tournaments > "Remove a team" takes any team out.
  - Director: "Record a result" and "Register a team" use stored procedures.
  - Step-by-step pictures: docs/ISTE330_Group1_Directions_With_Screenshots.docx
  - After most changes you can press Undo.
  - Stuck? Click Help at the top (or press F1).


ask to join from player perspective as well, not only coach
drop out from the tournament
more documentation to show the actuall samples on how to use the system

separate direction file, preferably with screenshots
