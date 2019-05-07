package tech.cryptonomic.conseil.metadata

// trait for Path
sealed trait Path {
  def up: Path
  def addLevel(nextLabel: String): Path
}

// case class representing an empty path
case class EmptyPath() extends Path {
  override def up: Path = this
  override def addLevel(platform: String): Path = PlatformPath(platform, this)
}

// case class representing a path for a platform
case class PlatformPath(platform: String, up: EmptyPath = EmptyPath()) extends Path {
  override def addLevel(networkPath: String): NetworkPath = NetworkPath(networkPath, this)
}

// case class representing a path for a network
case class NetworkPath(network: String, up: PlatformPath) extends Path {
  override def addLevel(entity: String): EntityPath = EntityPath(entity, this)
}

// case class representing a path for an entity
case class EntityPath(entity: String, up: NetworkPath) extends Path {
  override def addLevel(attribute: String): AttributePath = AttributePath(attribute, this)
}

// case class representing a path for an attribute
case class AttributePath(attribute: String, up: EntityPath) extends Path {
  override def addLevel(nextLabel: String): Path = throw new NotImplementedError()
}
