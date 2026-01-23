/**
 * Reference list of all gesture event types from the visionOS implementation.
 */

export interface GestureEventSpec {
  type: string
  category: 'tap' | 'drag' | 'rotate' | 'magnify'
  phase: 'single' | 'start' | 'update' | 'end'
  detailFields: string[]
}

/**
 * Complete list of gesture events that must be supported.
 */
export const GESTURE_EVENTS: GestureEventSpec[] = [
  // Tap
  {
    type: 'spatialtap',
    category: 'tap',
    phase: 'single',
    detailFields: ['location3D'],
  },

  // Drag
  {
    type: 'spatialdragstart',
    category: 'drag',
    phase: 'start',
    detailFields: ['location3D', 'startLocation3D', 'translation3D'],
  },
  {
    type: 'spatialdrag',
    category: 'drag',
    phase: 'update',
    detailFields: ['location3D', 'startLocation3D', 'translation3D'],
  },
  {
    type: 'spatialdragend',
    category: 'drag',
    phase: 'end',
    detailFields: ['location3D', 'translation3D'],
  },

  // Rotate
  {
    type: 'spatialrotatestart',
    category: 'rotate',
    phase: 'start',
    detailFields: ['rotation', 'location3D'],
  },
  {
    type: 'spatialrotate',
    category: 'rotate',
    phase: 'update',
    detailFields: ['rotation', 'location3D'],
  },
  {
    type: 'spatialrotateend',
    category: 'rotate',
    phase: 'end',
    detailFields: ['rotation', 'location3D'],
  },

  // Magnify
  {
    type: 'spatialmagnifystart',
    category: 'magnify',
    phase: 'start',
    detailFields: ['magnification', 'location3D'],
  },
  {
    type: 'spatialmagnify',
    category: 'magnify',
    phase: 'update',
    detailFields: ['magnification', 'location3D'],
  },
  {
    type: 'spatialmagnifyend',
    category: 'magnify',
    phase: 'end',
    detailFields: ['magnification', 'location3D'],
  },
]

/**
 * Get all gesture event types.
 */
export function getAllGestureEventTypes(): string[] {
  return GESTURE_EVENTS.map(e => e.type)
}

/**
 * Get gesture events by category.
 */
export function getGestureEventsByCategory(
  category: 'tap' | 'drag' | 'rotate' | 'magnify',
): GestureEventSpec[] {
  return GESTURE_EVENTS.filter(e => e.category === category)
}
