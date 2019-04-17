package tech.cryptonomic.conseil.metadata

sealed trait Path {
  def up: Path
  def addLevel(nextLabel: String): Path
}

case class EmptyPath() extends Path {
  override def up: Path = this
  override def addLevel(nextLabel: String): Path = PlatformPath(nextLabel)
}

case class PlatformPath(platform: String) extends Path {
  override def up: EmptyPath = EmptyPath()
  override def addLevel(nextLabel: String): NetworkPath = NetworkPath(platform, nextLabel)
}

case class NetworkPath(platform: String, network: String) extends Path {
  override def up: PlatformPath = PlatformPath(platform)
  override def addLevel(nextLabel: String): EntityPath = EntityPath(platform, network, nextLabel)
}

case class EntityPath(platform: String, network: String, entity: String) extends Path {
  override def up: NetworkPath = NetworkPath(platform, network)
  override def addLevel(nextLabel: String): AttributePath = AttributePath(platform, network, entity, nextLabel)
}

case class AttributePath(platform: String, network: String, entity: String, attribute: String) extends Path {
  override def up: EntityPath = EntityPath(platform, network, entity)
  override def addLevel(nextLabel: String): Path = throw new NotImplementedError()
}
