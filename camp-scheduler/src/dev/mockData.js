// Mock data for local Vite dev server (window.SCOUT_SIGHT_DATA not set)
export default {
  eagleRequiredBadges: [
    'First Aid MB', 'Communication MB', 'Cooking MB', 'Emergency Preparedness MB',
    'Lifesaving MB', 'Environmental Science MB', 'Swimming MB', 'Camping MB',
    'Citizenship in the World MB'
  ],
  scouts: [
    {
      memberId: '1001',
      name: 'Alex Johnson',
      patrol: 'Eagle Patrol',
      completedMeritBadges: ['Swimming MB', 'Camping MB'],
      partialMeritBadges: ['Archery MB'],
      completedRanks: ['Scout Rank', 'Tenderfoot Rank'],
      campRankProgress: {
        'Scout Rank':        { done: 15, total: 15, remaining: [] },
        'Tenderfoot Rank':   { done: 13, total: 13, remaining: [] },
        'Second Class Rank': { done: 14, total: 23, remaining: [
          { id: '6a', text: 'Identify or show evidence of at least 10 kinds of wild animals found in your local area or camping location.' },
          { id: '6b', text: 'Show evidence of at least three kinds of wild plants in your local area.' },
          { id: '6c', text: 'Identify two types of poisonous plants found in your local area.' },
          { id: '8a', text: 'Tell the transportation safety rules for your community.' },
          { id: '8b', text: 'Describe three career opportunities in a field related to the outdoors.' },
          { id: '9a', text: 'Participate in a school, community, or troop program on the dangers of using tobacco, alcohol, and other drugs.' },
          { id: '9b', text: 'Explain the BSA policies on the use of alcohol and tobacco products.' },
          { id: '9c', text: 'With your parent or guardian, discuss family rules.' },
        ]},
        'First Class Rank':  { done: 2,  total: 18, remaining: [
          { id: '3a', text: 'Discuss when it is appropriate to use a tourniquet.' },
          { id: '3b', text: 'Demonstrate first aid for the following: (a) Hypothermia' },
          { id: '3c', text: 'Show first aid for: (b) Heat exhaustion' },
          { id: '3d', text: 'Show first aid for: (c) Dehydration' },
          { id: '5a', text: 'Tell what precautions must be taken for a safe swim.' },
          { id: '5b', text: 'Demonstrate your ability to pass the BSA swimmer test.' },
          { id: '5c', text: 'Demonstrate water rescue methods.' },
          { id: '5d', text: 'Explain the importance of the buddy system.' },
          { id: '6b', text: 'Identify or show evidence of at least 10 wild animals.' },
          { id: '6c', text: 'Identify native plants in your area.' },
          { id: '6d', text: 'Identify invasive plants and methods of control.' },
          { id: '6e', text: 'Identify insects and explain their role in the ecosystem.' },
          { id: '7a', text: 'Demonstrate first aid for an unconscious victim.' },
          { id: '7b', text: 'Demonstrate first aid for a suspected fracture.' },
          { id: '7c', text: 'Show how to treat for shock.' },
          { id: '7d', text: 'Demonstrate proper way to transport an injured person.' },
        ]}
      },
      meritBadgeProgress: {
        'Archery MB': {
          done: 2, total: 9, remaining: [
            { id: '1', text: 'Do the following: State and demonstrate the range safety rules...' },
            { id: '2', text: 'Do the following: Name and point to the parts of an arrow.' },
            { id: '3', text: 'Explain the following: (a) The difference between a recurve bow and a compound bow.' },
            { id: '4', text: 'Show how to properly care for and store archery equipment.' },
            { id: '5', text: 'Participate in a tournament or field archery course.' },
            { id: '6', text: 'Learn about the history of archery.' },
            { id: '7', text: 'Find out about three career opportunities in archery.' },
          ]
        }
      }
    },
    {
      memberId: '1002',
      name: 'Sam Rivera',
      patrol: 'Eagle Patrol',
      completedMeritBadges: ['Canoeing MB', 'Archery MB'],
      partialMeritBadges: ['Rowing MB'],
      completedRanks: ['Scout Rank'],
      campRankProgress: {
        'Scout Rank':        { done: 15, total: 15, remaining: [] },
        'Tenderfoot Rank':   { done: 4,  total: 13, remaining: [
          { id: '3c', text: 'Demonstrate a practical use of the taut-line hitch.' },
          { id: '3d', text: 'Demonstrate proper care, sharpening, and use of the knife, saw, and ax.' },
          { id: '4c', text: 'Tell what you can do to prevent or reduce the occurrence of injuries.' },
          { id: '4d', text: 'Assemble a personal first-aid kit to carry with you on future campouts.' },
          { id: '5a', text: 'Explain the importance of the buddy system.' },
          { id: '5b', text: 'Describe what to do if you become lost on a hike or campout.' },
          { id: '5c', text: 'Explain the rules of safe hiking, both on the highway and cross-country.' },
          { id: '7a', text: 'Demonstrate how to display, raise, lower, and fold the U.S. flag.' },
          { id: '8',  text: 'Describe the steps in Scouting\'s Teaching EDGE method.' },
        ]},
        'Second Class Rank': { done: 0,  total: 23, remaining: [] },
        'First Class Rank':  { done: 0,  total: 18, remaining: [] }
      },
      meritBadgeProgress: {
        'Rowing MB': {
          done: 1, total: 7, remaining: [
            { id: '2', text: 'Before doing requirements 3 through 7, demonstrate your ability to pass the BSA swimmer test.' },
            { id: '3', text: 'Rowing: Row a straight course for 100 meters.' },
            { id: '4', text: 'Know about boat safety: lifejackets, communication, weather conditions.' },
            { id: '5', text: 'Rescue: Capsize a rowboat, right it, and bail it out.' },
            { id: '6', text: 'Row a canoe or rowboat for at least 30 minutes.' },
            { id: '7', text: 'Name career opportunities in fields related to rowing or watercraft.' },
          ]
        }
      }
    },
    {
      memberId: '1003',
      name: 'Jordan Lee',
      patrol: 'Bear Patrol',
      completedMeritBadges: [],
      partialMeritBadges: ['Canoeing MB', 'Cooking MB'],
      completedRanks: [],
      campRankProgress: {
        'Scout Rank':        { done: 9,  total: 15, remaining: [
          { id: '3a', text: 'Explain the patrol method.' },
          { id: '3b', text: 'Become familiar with your patrol name, emblem, flag, and yell.' },
          { id: '4a', text: 'Show how to tie a square knot, two half-hitches, and a taut-line hitch.' },
          { id: '4b', text: 'Show the proper care of a rope by whipping and fusing the ends.' },
          { id: '5',  text: 'Tell what you need to know about pocketknife safety.' },
          { id: '2b', text: 'Describe the four steps of Scout advancement.' },
        ]},
        'Tenderfoot Rank':   { done: 0,  total: 13, remaining: [] },
        'Second Class Rank': { done: 0,  total: 23, remaining: [] },
        'First Class Rank':  { done: 0,  total: 18, remaining: [] }
      },
      meritBadgeProgress: {
        'Canoeing MB': {
          done: 2, total: 9, remaining: [
            { id: '3', text: 'Before doing requirements 4 through 9, show that you can pass the BSA swimmer test.' },
            { id: '4', text: 'Paddle a canoe on a straight course for 50 meters.' },
            { id: '5', text: 'Demonstrate proper canoe safety procedures.' },
            { id: '6', text: 'Demonstrate rescue techniques: canoe-over-canoe and solo rescue.' },
            { id: '7', text: 'Paddle a canoe on a winding course through a series of markers.' },
            { id: '8', text: 'Capsize, right your canoe, and reenter it.' },
            { id: '9', text: 'Explain proper care and storage of a canoe.' },
          ]
        },
        'Cooking MB': {
          done: 3, total: 8, remaining: [
            { id: '4', text: 'Using the menu planned in requirement 3, cook three meals using a stove or camp stove.' },
            { id: '5', text: 'Cook a meal requiring multiple dishes using an outdoor stove.' },
            { id: '6', text: 'Using the outdoor skills you have developed, cook a breakfast, lunch, and dinner over an open fire.' },
            { id: '7', text: 'Present yourself to your Scoutmaster with your pack for inspection.' },
            { id: '8', text: 'Discuss with your counselor the importance of good nutrition.' },
          ]
        }
      }
    }
  ],
  campSchedule: {
    dailyClasses: [
      {
        meritBadges: [], ranks: ['Scout Rank', 'Tenderfoot Rank'],
        sessions: [{ start: '9:00', end: '10:00' }]
      },
      {
        meritBadges: [], ranks: ['Second Class Rank'],
        sessions: [{ start: '10:00', end: '11:00' }]
      },
      {
        meritBadges: [], ranks: ['First Class Rank'],
        sessions: [{ start: '11:00', end: '12:00' }]
      },
      {
        meritBadges: ['Archery MB'], ranks: [],
        sessions: [
          { start: '9:00', end: '10:30' },
          { start: '10:30', end: '12:00' }
        ]
      },
      {
        meritBadges: ['Canoeing MB'], ranks: [],
        sessions: [
          { start: '9:00', end: '10:30' },
          { start: '10:30', end: '12:00' }
        ]
      },
      {
        meritBadges: ['Swimming MB'], ranks: [],
        sessions: [
          { start: '9:00', end: '10:30' },
          { start: '10:30', end: '12:00' }
        ]
      },
      {
        meritBadges: ['Rowing MB'], ranks: [],
        sessions: [{ start: '10:30', end: '12:00' }]
      },
      {
        meritBadges: ['Cooking MB'], ranks: [],
        sessions: [{ start: '9:00', end: '10:30' }]
      },
      {
        meritBadges: ['Art MB', 'Animation MB'], ranks: [],
        sessions: [
          { start: '9:00', end: '10:00' },
          { start: '11:00', end: '12:00' }
        ]
      }
    ],
    freeTimeClasses: [
      { meritBadges: ['Basketry MB'], day: 'Monday',    time: '3:45' },
      { meritBadges: ['Basketry MB'], day: 'Tuesday',   time: '3:45' },
      { meritBadges: ['Basketry MB'], day: 'Wednesday', time: '3:45' },
      { meritBadges: ['Leatherwork MB'], day: 'Monday',    time: '3:45' },
      { meritBadges: ['Leatherwork MB'], day: 'Tuesday',   time: '3:45' },
      { meritBadges: ['Woodcarving MB'], day: 'Wednesday', time: '3:45' },
      { meritBadges: ['Woodcarving MB'], day: 'Thursday',  time: '3:45' }
    ]
  },
  campConfig: {
    campName: 'Camp Parsons',
    meritBadges: [
      'Archery MB', 'Art MB', 'Animation MB', 'Basketry MB', 'Canoeing MB',
      'Cooking MB', 'Leatherwork MB', 'Rowing MB', 'Swimming MB', 'Woodcarving MB'
    ],
    rankCoverage: {
      'Scout Rank':        ['1a','1b','1c','1d','1e','1f','2a','2b','2c','2d','3a','3b','4a','4b','5'],
      'Tenderfoot Rank':   ['3a','3b','3c','3d','4a','4b','4c','4d','5a','5b','5c','7a','8'],
      'Second Class Rank': ['1b','2a','2b','2c','2d','2f','2g','3a','3b','3c','3d','5a','5c','5d','6a','6b','6c','6d','6e','8a','8b','9a','9b'],
      'First Class Rank':  ['1b','3a','3b','3c','3d','5a','5b','5c','5d','6b','6c','6d','6e','7a','7b','7c','7d','7e','7f']
    }
  }
}
