/**
 * Reference list of all WebSpatial commands from the visionOS implementation.
 * Used to verify Android XR has full compatibility.
 */

export interface CommandSpec {
  name: string
  category: string
  description: string
  parameters: string[]
  returnsData: boolean
}

/**
 * Complete list of WebSpatial commands that must be implemented.
 */
export const WEBSPATIAL_COMMANDS: CommandSpec[] = [
  // Scene Management
  {
    name: 'UpdateSpatialSceneProperties',
    category: 'scene',
    description: 'Update scene corner radius, opacity, and background material',
    parameters: ['cornerRadius', 'opacity', 'material'],
    returnsData: false,
  },
  {
    name: 'UpdateSceneConfig',
    category: 'scene',
    description: 'Update scene configuration like size and resizability',
    parameters: [
      'defaultSize',
      'resizability',
      'worldScaling',
      'worldAlignment',
    ],
    returnsData: false,
  },
  {
    name: 'GetSpatialSceneState',
    category: 'scene',
    description: 'Get current state of the spatial scene',
    parameters: [],
    returnsData: true,
  },
  {
    name: 'FocusScene',
    category: 'scene',
    description: 'Focus a specific scene',
    parameters: ['id'],
    returnsData: false,
  },

  // Spatialized 2D Elements
  {
    name: 'UpdateSpatialized2DElementProperties',
    category: '2d-element',
    description:
      'Update 2D element position, size, material, and gesture flags',
    parameters: [
      'id',
      'clientX',
      'clientY',
      'width',
      'height',
      'backOffset',
      'opacity',
      'visible',
      'zIndex',
      'cornerRadius',
      'material',
      'enableTapGesture',
      'enableDragGesture',
      'enableRotateGesture',
      'enableMagnifyGesture',
    ],
    returnsData: false,
  },
  {
    name: 'UpdateSpatializedElementTransform',
    category: '2d-element',
    description: 'Apply 4x4 transform matrix to element',
    parameters: ['id', 'matrix'],
    returnsData: false,
  },
  {
    name: 'AddSpatializedElementToSpatialScene',
    category: '2d-element',
    description: 'Add a spatialized element to a scene',
    parameters: ['spatializedElementId'],
    returnsData: false,
  },
  {
    name: 'AddSpatializedElementToSpatialized2DElement',
    category: '2d-element',
    description: 'Nest a spatialized element inside another',
    parameters: ['id', 'spatializedElementId'],
    returnsData: false,
  },

  // Static 3D Elements
  {
    name: 'CreateSpatializedStatic3DElement',
    category: 'static-3d',
    description: 'Create a 3D model element from URL',
    parameters: ['modelURL'],
    returnsData: true,
  },
  {
    name: 'UpdateSpatializedStatic3DElementProperties',
    category: 'static-3d',
    description: 'Update static 3D element properties',
    parameters: ['id', 'opacity', 'visible', 'transform'],
    returnsData: false,
  },

  // Dynamic 3D Elements
  {
    name: 'CreateSpatializedDynamic3DElement',
    category: 'dynamic-3d',
    description: 'Create a dynamic 3D element container',
    parameters: [],
    returnsData: true,
  },
  {
    name: 'UpdateSpatializedDynamic3DElementProperties',
    category: 'dynamic-3d',
    description: 'Update dynamic 3D element properties',
    parameters: ['id', 'opacity', 'visible', 'transform'],
    returnsData: false,
  },

  // Entity System
  {
    name: 'CreateSpatialEntity',
    category: 'entity',
    description: 'Create a new spatial entity',
    parameters: ['name'],
    returnsData: true,
  },
  {
    name: 'UpdateEntityProperties',
    category: 'entity',
    description: 'Update entity transform',
    parameters: ['entityId', 'transform'],
    returnsData: false,
  },
  {
    name: 'AddEntityToDynamic3D',
    category: 'entity',
    description: 'Add entity to a dynamic 3D element',
    parameters: ['dynamic3dId', 'entityId'],
    returnsData: false,
  },
  {
    name: 'AddEntityToEntity',
    category: 'entity',
    description: 'Add entity as child of another entity',
    parameters: ['parentId', 'childId'],
    returnsData: false,
  },
  {
    name: 'RemoveEntityFromParent',
    category: 'entity',
    description: 'Remove entity from its parent',
    parameters: ['entityId'],
    returnsData: false,
  },
  {
    name: 'SetParentToEntity',
    category: 'entity',
    description: 'Set parent of an entity',
    parameters: ['childId', 'parentId'],
    returnsData: false,
  },

  // Geometry
  {
    name: 'CreateGeometry',
    category: 'geometry',
    description: 'Create geometry (box, plane, sphere, cone, cylinder)',
    parameters: ['type', 'width', 'height', 'depth', 'radius', 'cornerRadius'],
    returnsData: true,
  },

  // Materials
  {
    name: 'CreateUnlitMaterial',
    category: 'material',
    description: 'Create an unlit material',
    parameters: ['color', 'textureId', 'transparent', 'opacity'],
    returnsData: true,
  },

  // Model Components
  {
    name: 'CreateModelComponent',
    category: 'component',
    description: 'Create a model component with geometry and materials',
    parameters: ['geometryId', 'materialIds'],
    returnsData: true,
  },
  {
    name: 'AddComponentToEntity',
    category: 'component',
    description: 'Add a component to an entity',
    parameters: ['entityId', 'componentId'],
    returnsData: false,
  },

  // Model Assets
  {
    name: 'CreateModelAsset',
    category: 'model',
    description: 'Load an external 3D model asset',
    parameters: ['url'],
    returnsData: true,
  },
  {
    name: 'CreateSpatialModelEntity',
    category: 'model',
    description: 'Create an entity for a model',
    parameters: [],
    returnsData: true,
  },

  // Lifecycle
  {
    name: 'Destroy',
    category: 'lifecycle',
    description: 'Destroy a spatial object',
    parameters: ['id'],
    returnsData: false,
  },
  {
    name: 'Inspect',
    category: 'lifecycle',
    description: 'Debug inspect an object',
    parameters: ['id'],
    returnsData: true,
  },

  // Legacy/Compat
  {
    name: 'updateResource',
    category: 'legacy',
    description: 'Legacy resource update',
    parameters: ['update'],
    returnsData: false,
  },
  {
    name: 'CheckWebViewCanCreate',
    category: 'legacy',
    description: 'Check if webview can create windows',
    parameters: [],
    returnsData: true,
  },
]

/**
 * Get all command names.
 */
export function getAllCommandNames(): string[] {
  return WEBSPATIAL_COMMANDS.map(c => c.name)
}

/**
 * Get commands by category.
 */
export function getCommandsByCategory(category: string): CommandSpec[] {
  return WEBSPATIAL_COMMANDS.filter(c => c.category === category)
}

/**
 * Get all unique categories.
 */
export function getAllCategories(): string[] {
  return [...new Set(WEBSPATIAL_COMMANDS.map(c => c.category))]
}
