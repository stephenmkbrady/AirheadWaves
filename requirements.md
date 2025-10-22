# CosmicCast Requirements - Version Index

This document serves as an index to all versions of the CosmicCast (formerly AirheadWaves) requirements documentation.

## Current Version
**→ [requirements-v1.1.md](requirements-v1.1.md)** - Current implementation state (transmit-only)

## All Versions

### Version 1.1 - Current Implementation (Active)
- **File**: [requirements-v1.1.md](requirements-v1.1.md)
- **Date**: 2025-10-22
- **Status**: ✅ Active - Reflects current codebase
- **Scope**: Transmit-only audio streaming application
- **Key Features**:
  - Internal audio capture using MediaProjection
  - AAC encoding with ADTS framing
  - TCP streaming to remote servers
  - Real-time audio effects (bass, treble, volume)
  - Multi-profile management
  - Jetpack Compose UI with audio visualization
  - Background foreground service

### Version 2.0 - Receive Mode Features (Planned)
- **File**: [requirements-v2.0.md](requirements-v2.0.md)
- **Date**: 2025-10-18 (original draft)
- **Status**: 📝 Future - Not yet implemented
- **Purpose**: Add receive mode capability to complement existing transmit mode
- **Scope**: Phase 1 & Phase 2 receive mode implementation
- **Key Features**:
  - Receive mode core functionality (TCP server)
  - AAC decoding and audio playback
  - Dual profile types (transmit/receive separation)
  - Audio output routing
  - Buffering strategies
  - Enhanced error recovery
  - Audio focus management

## Version History

| Version | Date | Status | Description |
|---------|------|--------|-------------|
| 1.1 | 2025-10-22 | Active | Current implementation - transmit-only streaming |
| 2.0 | TBD | Planned | Receive mode features (future) |

## Versioning Guidelines

### When to create a new version:

**Minor version (1.x → 1.y):**
- Updates reflecting actual implemented changes
- Bug fixes and small feature additions
- Clarifications to existing requirements
- Status updates based on development progress

**Major version (1.x → 2.x):**
- New major features (e.g., receive mode)
- Significant architectural changes
- New development phases
- Major scope expansion

### Version workflow:
1. **v1.1** = Current state of the application (what's implemented NOW)
2. **v2.0** = Next major feature set (planned receive mode)
3. When v2.0 is implemented, it becomes the new current version
4. Future planning goes into v3.0, etc.

### Document Relationships:
- **v1.1** documents what EXISTS in the codebase today
- **v2.0** documents what's PLANNED for the next major release
- All versions preserved for historical reference

## Related Documents
- [F-DROID-SUBMISSION.md](F-DROID-SUBMISSION.md) - F-Droid submission checklist
- [instructions.md](instructions.md) - Implementation instructions
- README.md - Project overview

## Notes
- v1.1 is the current active requirements reflecting the transmit-only implementation
- v2.0 contains plans for future receive mode functionality
- The index always points to the current active version at the top
