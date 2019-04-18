package tech.cryptonomic.conseil.metadata

sealed trait Path {
  def up: Path
  def addLevel(nextLabel: String): Path
}

case class EmptyPath() extends Path {
  override def up: Path = this
  override def addLevel(platform: String): Path = PlatformPath(platform, this)
}

case class PlatformPath(platform: String, up: EmptyPath = EmptyPath()) extends Path {
  override def addLevel(networkPath: String): NetworkPath = NetworkPath(networkPath, this)
}

case class NetworkPath(network: String, up: PlatformPath) extends Path {
  override def addLevel(entity: String): EntityPath = EntityPath(entity, this)
}

case class EntityPath(entity: String, up: NetworkPath) extends Path {
  override def addLevel(attribute: String): AttributePath = AttributePath(attribute, this)
}

case class AttributePath(attribute: String, up: EntityPath) extends Path {
  override def addLevel(nextLabel: String): Path = throw new NotImplementedError()
}
